package org.elkoserver.util;

import com.grack.nanojson.JsonParserException;
import org.elkoserver.json.JsonDecodingException;
import org.elkoserver.json.JsonObject;
import org.elkoserver.json.JsonParsing;

import java.io.*;
import java.util.LinkedList;

import static java.nio.charset.Charset.defaultCharset;
import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * Standalone command-line application to read and parse JSON object
 * descriptors and indicate any problems found.
 */
class JsonValidator {
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
     * Print command line usage information and then exit.
     */
    private static void usage() {
        e("usage: java JsonValidator [opts...]");
        e("  [-f[ile]] FILE      Use FILE as an input source");
        e("  -                   Use stdin as an input source");
        e("  -h[elp] or -?       Output this usage information");
        System.exit(0);
    }

    /**
     * Validate the contents of a file as a JSON object.
     *
     * @param source  Pathname of the file, or "-" to read stdin.
     */
    private static String validate(String source) {
        File file = new File(source);
        BufferedReader in;
        try {
            if (source.equals("-")) {
                in = new BufferedReader(new InputStreamReader(System.in, defaultCharset()));
            } else {
                in = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF_8));
            }
        } catch (FileNotFoundException e) {
            e("bad " + source + " not found");
            return null;
        }

        StringBuilder inBuf = new StringBuilder();
        String line;
        do {
            try {
                line = in.readLine();
            } catch (IOException e) {
                e("problem reading file: " + e);
                return null;
            }
            if (line != null) {
                inBuf.append(' ');
                inBuf.append(line);
            }
        } while (line != null);
        try {
            JsonObject obj = JsonParsing.INSTANCE.jsonObjectFromString(inBuf.toString());
            return obj.getString("ref");
        } catch (JsonParserException e) {
            e("bad " + source + " syntax error: " + e.getMessage());
            return null;
        } catch (JsonDecodingException e) {
            e("bad " + source + " object has no ref string");
            return null;
        }
    }

    /**
     * Program main: parse command line flags, then scan each input source and
     * do the appropriate things with it.
     */
    public static void main(String[] args) {
        LinkedList<String> sources = new LinkedList<>();
        try {
            for (int i = 0; i < args.length; ) {
                String arg = args[i++];
                if (arg.startsWith("-")) {
                    switch (arg) {
                        case "-file":
                        case "-f":
                            sources.add(args[i++]);
                            break;
                        case "-":
                            sources.add("-");
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
                    sources.add(arg);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            usage();
        }
        boolean allOK = true;
        for (String src : sources) {
            String ref = validate(src);
            if (ref != null) {
                p("ok " + src + " (" + ref + ")");
            } else {
                allOK = false;
            }
            if (!allOK) {
                System.exit(1);
            }
        }
    }
}
