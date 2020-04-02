package org.elkoserver.util.trace.logeater;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.elkoserver.json.JSONArray;
import org.elkoserver.json.JSONObject;
import org.elkoserver.util.trace.Level;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.Charset.defaultCharset;

/**
 * Standalone command-line application to read and parse one or more
 * server log files and selectively dump the contents into a MongoDB
 * collection for analysis and/or archiving.
 *
 * Each instance of LogEater consumes a single log file.
 */
class LogEater {
    /** Reader for parsing the log file. */
    private BufferedReader myIn;

    /** Description of the log file. */
    private SourceInfo mySource;

    /** Sequence number to track entry order despite duplicated timestamps. */
    private int mySeq;

    /** MongoDB object ID of the source descriptor record for this log. */
    private Object mySourceID;

    /* These statics capture global parameterization from the command line. */

    /** If true, output debug info to help determine if it's working right. */
    private static boolean amTesting = false;

    /** If true, process communications message entries. */
    private static boolean amEatingComm = false;

    /** If true, process server debug entries. */
    private static boolean amEatingDebug = false;

    /** If true, record processed entries in the database. */
    private static boolean amWriting = true;

    /** The MongoDB collection into which things will be recorded. */
    private static MongoCollection<Document> theCollection;


    /**
     * Convenience function for writing an error message to stderr.
     *
     * @param line  The line to write.
     */
    private static void e(String line) {
        System.err.println(line);
    }

    /**
     * Convenience function for writing a diagnostics message to stdout.
     *
     * @param line  The line to write.
     */
    private static void p(String line) {
        System.out.println(line);
    }

    /**
     * Simple struct class to hold the description of a log file.
     */
    private static class SourceInfo {
        /** Where did this come from? */
        String path;
        /** What sort of log is this (e.g., "context", "workshop", etc.) */
        String type;
        /** Distinguishing label (e.g., "Foon prod", "Fred's test") */
        String label;
    }

    /**
     * Print command line usage information and then exit.
     */
    private static void usage() {
        e("usage: java LogEater [opts...]");
        e("  [-f[ile]] FILE      Use FILE as an input source");
        e("  -                   Use stdin as an input source");
        e("  -l[abel] LABEL      Tag the next input source with LABEL");
        e("  -t[type] TYPE       Record the next input source as type TYPE");
        e("");
        e("  -c[omm]             Eat message traffic logs");
        e("  -noc[omm]           Don't eat message traffic logs {default}");
        e("  -d[ebug]            Eat server debug logs");
        e("  -nod[ebug]          Don't eat server debug logs {default}");
        e("  -all                Equivalent to -c -m -d");
        e("");
        e("  -dbhost HOST:PORT   MongoDB is at HOST:PORT {localhost:27017}");
        e("  -db DBNAME          Use MongoDB database DBNAME {elko}");
        e("  -dbcoll COLL        Use MongoDB collection COLL {logeater}");
        e("  -nodb               Don't write to the database");
        e("");
        e("  -test               Spew diagnostic output to stdout");
        e("  -h[elp] or -?       Output this usage information");
        System.exit(0);
    }

    /**
     * Program main: parse command line flags, then scan each input source and
     * do the appropriate things with it.
     */
    public static void main(String[] args) {
        LinkedList<SourceInfo> sources = new LinkedList<>();
        SourceInfo source = new SourceInfo();
        String dbHost = "localhost:27017";
        String dbName = "elko";
        String dbCollName = "logeater";

        try {
            for (int i = 0; i < args.length; ) {
                String arg = args[i++];
                if (arg.startsWith("-")) {
                    switch (arg) {
                        case "-test":
                            amTesting = true;
                            break;
                        case "-comm":
                        case "-c":
                            amEatingComm = true;
                            break;
                        case "-nocomm":
                        case "-noc":
                            amEatingComm = false;
                            break;
                        case "-debug":
                        case "-d":
                            amEatingDebug = true;
                            break;
                        case "-nodebug":
                        case "-nod":
                            amEatingDebug = false;
                            break;
                        case "-all":
                            amEatingComm = true;
                            amEatingDebug = true;
                            break;
                        case "-file":
                        case "-f":
                            if (source.path != null) {
                                sources.add(source);
                                source = new SourceInfo();
                            }
                            source.path = args[i++];
                            break;
                        case "-":
                            if (source.path != null) {
                                sources.add(source);
                                source = new SourceInfo();
                            }
                            source.path = arg;
                            break;
                        case "-label":
                        case "-l":
                            source.label = args[i++];
                            break;
                        case "-type":
                        case "-t":
                            source.type = args[i++];
                            break;
                        case "-dbhost":
                            dbHost = args[i++];
                            break;
                        case "-db":
                            dbName = args[i++];
                            break;
                        case "-dbcoll":
                            dbCollName = args[i++];
                            break;
                        case "-nodb":
                            amWriting = false;
                            break;
                        case "-help":
                        case "-h":
                        case "-?":
                            usage();
                            break;
                        default:
                            e("ignoring unknown command line flag: " + arg);
                            usage();
                            break;
                    }
                } else {
                    if (source.path != null) {
                        sources.add(source);
                        source = new SourceInfo();
                    }
                    source.path = arg;
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            usage();
        }
        if (source.path != null) {
            sources.add(source);
        }
        if (amWriting) {
            int colon = dbHost.indexOf(':');
            int port;
            String host;
            if (colon < 0) {
                port = 27017;
                host = dbHost;
            } else {
                port = Integer.parseInt(dbHost.substring(colon + 1)) ;
                host = dbHost.substring(0, colon);
            }
            MongoClient mongo;
                mongo = new MongoClient(host, port);
            MongoDatabase db = mongo.getDatabase(dbName);
            theCollection = db.getCollection(dbCollName);
        }
        for (SourceInfo src : sources) {
            LogEater eater = new LogEater(src);
            if (eater.ok()) {
                eater.eatFile();
            }
        }
    }

    /**
     * Construct a log eater for an input source.
     *
     * @param source  Description of the source.
     */
    private LogEater(SourceInfo source) {
        mySeq = 1;
        File file = new File(source.path);
        if (source.label == null) {
            source.label = file.getName();
        }
        if (source.type != null) {
            source.type = "-";
        }
        try {
            if (source.path.equals("-")) {
                myIn = new BufferedReader(new InputStreamReader(System.in, defaultCharset()));
            } else {
                myIn = new BufferedReader(new InputStreamReader(new FileInputStream(file), defaultCharset()));
            }
            mySource = source;
        } catch (FileNotFoundException e) {
            e("File '" + source.path + "' not found");
            myIn = null;
        }
    }

    /**
     * Test if this instance initialized properly.
     *
     * @return true if we're good to go, false if this instance should not be
     *    used.
     */
    private boolean ok() {
        return myIn != null;
    }

    /**
     * Write a record to the database describing the input source.
     */
    private void recordSource() {
        Document out = new Document();
        out.put("tag", "src");
        out.put("path", mySource.path);
        out.put("name", mySource.label);
        out.put("srctype", mySource.type);
        theCollection.insertOne(out);
        mySourceID = out.get("_id");
    }        

    /**
     * Scan the entire input source and process each line appropriately.
     */
    private void eatFile() {
        if (amWriting) {
            recordSource();
        }
        String line;
        do {
            try {
                line = myIn.readLine();
            } catch (IOException e) {
                e("problem reading file: " + e);
                break;
            }
            if (line != null) {
                if (!eatLine(line)) {
                    break;
                }
            }
        } while (line != null);
    }

    /** Regexp to interpret the canonical information in each log line
     *
     * Each line looks like:
     *
     *      - YYYY/MM/DD hh:mm:ss.fff TAG subsystem : otherstuff
     *   or
     *      - YYYY/MM/DD hh:mm:ss.fff TAG subsystem (sourceloc) otherstuff
     *  
     * The otherstuff part varies according to the TAG and is processed by
     * separately.
     */
    private static final Pattern COARSE_PATTERN =
        Pattern.compile("- (\\d{4})/(\\d{2})/(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3}) ([-a-zA-Z0-9]+) ([-a-zA-Z0-9]+) (:|\\(([^)]*)\\)) (.*)");

    /**
     * Scan a single log line and process it appropriately.
     *
     * @param line  The line to be scanned
     *
     * @return true if successful, false if there was a problem.
     */
    private boolean eatLine(String line) {
        if (line.charAt(0) == '-') {
            Matcher matcher = COARSE_PATTERN.matcher(line);
            if (matcher.matches()) {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                int hour = Integer.parseInt(matcher.group(4));
                int minute = Integer.parseInt(matcher.group(5));
                int second = Integer.parseInt(matcher.group(6));
                int milli = Integer.parseInt(matcher.group(7));

                Calendar cal = Calendar.getInstance();
                cal.set(year, month - 1, day, hour, minute, second);
                cal.set(Calendar.MILLISECOND, milli);
                long timestamp = cal.getTimeInMillis();
                
                String tag = matcher.group(8);
                String subsystem = matcher.group(9);
                String location = matcher.group(11);
                String message = matcher.group(12);
                eatParsedLine(timestamp, tag, subsystem, location, message);
                return true;
            } else {
                e("malformed line /" + line + "/");
                return false;
            }
        } else {
            return true;
        }
    }

    /** Table mapping from message tags to logging levels. */
    private static Map<String, Level> tags = new HashMap<>();
    static {
        tags.put("NTC", Level.NOTICE);
        tags.put("MSG", Level.MESSAGE);
        tags.put("ERR", Level.ERROR);
        tags.put("WRN", Level.WARNING);
        tags.put("WLD", Level.WORLD);
        tags.put("USE", Level.USAGE);
        tags.put("EVN", Level.EVENT);
        tags.put("DBG", Level.DEBUG);
        tags.put("VRB", Level.VERBOSE);
    }

    /** Regexp to parse the message field of a comm entry. */
    private static final Pattern COMM_PATTERN =
        Pattern.compile("([^ ]+) (<-|->) (.*)");

    /**
     * Process a parsed log line.
     *
     * @param timestamp  The message timestamp.
     * @param tagStr  The message tag.
     * @param subsystem  The logging subsystem.
     * @param location  The source code location info, or null.
     * @param message  The tag-specific message field.
     *
     */
    private void eatParsedLine(long timestamp, String tagStr,
                               String subsystem, String location,
                               String message)
    {
        Level tag = tags.get(tagStr);
        if (tag == null) {
            e("unknown log tag '" + tagStr + "'");
            return;
        }
        switch (tag) {
            case NOTICE:
                if (amEatingDebug) {
                    processInfo(timestamp, subsystem, message);
                    return;
                } else {
                    return;
                }
            case MESSAGE:
                if (amEatingComm) {
                    Matcher commMatcher = COMM_PATTERN.matcher(message);
                    if (commMatcher.matches()) {
                        String connection = commMatcher.group(1);
                        String direction = commMatcher.group(2);
                        boolean inbound = direction.equals("->");
                        String msg = commMatcher.group(3);
                        processComm(timestamp, subsystem, connection,
                                inbound, msg);
                        return;
                    } else {
                        e("malformed comm message /" + message + "/");
                        return;
                    }
                } else {
                    return;
                }
            case ERROR:
            case WARNING:
            case WORLD:
            case USAGE:
            case EVENT:
            case DEBUG:
            case VERBOSE:
                if (amEatingDebug) {
                    processDebug(timestamp, subsystem, tag, location,
                            message);
                }
        }
    }

    /**
     * Create a new MongoDB object and initialize it with the stuff that is
     * common to all log records.
     *
     * @param timestamp  The message timestamp.
     * @param tag  The message tag.
     * @param subsystem  The logging subsystem.
     *
     * @return the newly create DBObject.
     */
    private Document baseDBObject(long timestamp, String tag, String subsystem)
    {
        Document out = new Document();
        out.put("src", mySourceID);
        out.put("seq", mySeq++);
        out.put("ts", timestamp);
        out.put("tag", tag);
        out.put("sys", subsystem);
        return out;
    }

    /**
     * Translate a JSON property value to its corresponding DBObject value.
     *
     * @param value  The value to be thus translated.
     *
     * @return an object equivalent to 'value' but suitable for storing into
     *    a DBObject property.
     */
    private Object valueToDBValue(Object value) {
        if (value instanceof JSONObject) {
            value = jsonObjectToDBObject((JSONObject) value);
        } else if (value instanceof JSONArray) {
            value = jsonArrayToDBArray((JSONArray) value);
        } else if (value instanceof Long) {
            long intValue = (Long) value;
            if (Integer.MIN_VALUE <= intValue &&
                intValue <= Integer.MAX_VALUE) {
                value = (int) intValue;
            }
        }
        return value;
    }

    /**
     * Translate a JSON array into a corresponding DBObject array.
     *
     * @param arr  The array to be tranlsated.
     *
     * @return an ArrayList equivalent to 'arr' that can be used in a DBObject.
     */
    private ArrayList<Object> jsonArrayToDBArray(JSONArray arr) {
        ArrayList<Object> result = new ArrayList<>(arr.size());
        for (Object elem : arr) {
            result.add(valueToDBValue(elem));
        }
        return result;
    }

    /**
     * Translate a JSON object into a DBObject.
     *
     * @param obj  The JSON object to be translated.
     *
     * @return a DBObject equivalent to 'obj' that can be stored as a MongoDB
     *    record.
     */
    private Document jsonObjectToDBObject(JSONObject obj) {
        Document result = new Document();
        for (Map.Entry<String, Object> prop : obj.properties()) {
            result.put(prop.getKey(), valueToDBValue(prop.getValue()));
        }
        return result;
    }

    /**
     * Process an info (NTC) entry.
     *
     * @param timestamp  The message timestamp.
     * @param subsystem  The logging subsystem.
     * @param message  The free-form message text.
     *
     */
    private void processInfo(long timestamp, String subsystem,
                             String message)
    {
        if (amTesting) {
            String timestr =
                String.format("%1$tY/%1$tm/%1$td %1$tT.%1$tL", timestamp);
            p("- " + timestr + " NTC " + subsystem + " : " + message);
        }
        if (amWriting) {
            Document out = baseDBObject(timestamp, "NTC", subsystem);
            out.put("msg", message);
            theCollection.insertOne(out);
        }
    }
    
    /**
     * Process a comm (MSG) entry.
     *
     * @param timestamp  The message timestamp.
     * @param subsystem  The logging subsystem.
     * @param connection  Identifier of the connection over which the message
     *    was sent or received.
     * @param inbound  True if the message was received, false if it was sent.
     * @param msg  The JSON message itself.
     *
     */
    private void processComm(long timestamp, String subsystem,
                             String connection, boolean inbound, String msg)
    {
        if (amTesting) {
            String timestr =
                String.format("%1$tY/%1$tm/%1$td %1$tT.%1$tL", timestamp);
            String direction = inbound ? " -> " : " <- ";
            p("- " + timestr + " MSG " + subsystem + " : " + connection +
              direction + msg);
        }
        if (amWriting) {
            Document out = baseDBObject(timestamp, "MSG", subsystem);
            out.put("conn", connection);
            out.put("in", inbound);
            out.put("msg", msg);
            theCollection.insertOne(out);
        }
    }

    /**
     * Process a debug entry.
     *
     * @param timestamp  The message timestamp.
     * @param subsystem  The logging subsystem.
     * @param level  The logging level the message was recorded at.
     * @param location  The source text location, or null.
     * @param message  The free-form message text.
     *
     */
    private void processDebug(long timestamp, String subsystem, Level level,
                              String location, String message)
    {
        String tag = level.getTerseCode();
        if (amTesting) {
            String timestr =
                String.format("%1$tY/%1$tm/%1$td %1$tT.%1$tL", timestamp);
            String locationStr;
            if (location == null) {
                locationStr = ":";
            } else {
                locationStr = "(" + location + ")";
            }
            p("- " + timestr + " " + tag + " " + subsystem + " " +
              locationStr + " " + message);
        }
        if (amWriting) {
            Document out = baseDBObject(timestamp, tag, subsystem);
            if (location != null) {
                out.put("loc", location);
            }
            out.put("msg", message);
            theCollection.insertOne(out);
        }
    }
}
