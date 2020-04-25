package org.elkoserver.objdb.store.filestore;

import org.elkoserver.foundation.properties.ElkoProperties;
import org.elkoserver.json.JsonArray;
import org.elkoserver.json.JsonObject;
import org.elkoserver.objdb.store.*;
import org.elkoserver.util.trace.Trace;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.elkoserver.json.JsonParsing.jsonObjectFromString;

/**
 * A simple {@link ObjectStore} implementation that stores objects in text
 * files, one file per object.  Each file contains a JSON-encoded
 * representation of the object it stores.
 */
public class FileObjectStore implements ObjectStore {

    /** The directory in which the object "database" contents are stored. */
    private File myODBDirectory;

    /**
     * Constructor.  Currently there is nothing to do, since all the real
     * initialization work happens in {@link #initialize initialize()}.
     */
    public FileObjectStore() {
    }

    /**
     * Do the initialization required to begin providing object store
     * services.
     *
     * <p>The property <code>"<i>propRoot</i>.odb"</code> should specify the
     * pathname of the directory in which the object description files are
     * stored.
     *
     * @param props  Properties describing configuration information.
     * @param propRoot  Prefix string for selecting relevant properties.
     * @param appTrace  Trace object for use in logging.
     */
    public void initialize(ElkoProperties props, String propRoot,
                           Trace appTrace)
    {
        /* Trace object for diagnostics. */

        String dirname = props.getProperty(propRoot + ".odb");
        if (dirname == null) {
            appTrace.fatalError("no object database directory specified");
            throw new IllegalStateException();
        }
        myODBDirectory = new File(dirname);
        if (!myODBDirectory.exists()) {
            appTrace.fatalError("object database directory '" + dirname +
                          "' does not exist");
            throw new IllegalStateException();
        } else if (!myODBDirectory.isDirectory()) {
            appTrace.fatalError("requested object database directory " + dirname +
                          " is not a directory");
            throw new IllegalStateException();
        }
    }

    /**
     * Obtain the object or objects that a field value references.
     *
     * @param value  The value to dereference.
     * @param results  List in which to place the object or objects obtained.
     */
    private void dereferenceValue(Object value, List<ObjectDesc> results) {
        if (value instanceof JsonArray) {
            for (Object elem : (JsonArray) value) {
                if (elem instanceof String) {
                    results.addAll(doGet((String) elem));
                }
            }
        } else if (value instanceof String) {
            results.addAll(doGet((String) value));
        }
    }

    /**
     * Perform a single 'get' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be gotten.
     *
     * @return a list of ObjectDesc objects, the first of which will be
     *    the result of getting 'ref' and the remainder, if any, will be the
     *    results of getting any contents objects.
     */
    private List<ObjectDesc> doGet(String ref) {
        List<ObjectDesc> results = new LinkedList<>();

        String failure = null;
        String obj = null;
        List<ObjectDesc> contents = null;
        try {
            File file = odbFile(ref);
            long length = file.length();
            if (length > 0) {
                Reader objReader = new InputStreamReader(new FileInputStream(file), UTF_8);
                char[] buf = new char[(int) length];
                // FIXME: Handle the result
                objReader.read(buf);
                objReader.close();
                obj = new String(buf);
                JsonObject jsonObj = jsonObjectFromString(obj);
                contents = doGetContents(jsonObj);
            } else {
                failure = "not found";
            }
        } catch (Exception e) {
            obj = null;
            failure = e.getMessage();
        }

        results.add(new ObjectDesc(ref, obj, failure));
        if (contents != null) {
            results.addAll(contents);
        }
        return results;
    }

    /**
     * Fetch the contents of an object.
     *
     * @param obj  The object whose contents are sought.
     *
     * @return a List of ObjectDesc objects for the contents as
     *    requested.
     */
    private List<ObjectDesc> doGetContents(JsonObject obj) {
        List<ObjectDesc> results = new LinkedList<>();
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            String propName = entry.getKey();
            if (propName.startsWith("ref$")) {
                dereferenceValue(entry.getValue(), results);
            }
        }
        return results;
    }

    /**
     * Perform a single 'put' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be written.
     * @param obj  JSON string encoding the object to be written.
     *
     * @return a ResultDesc object describing the success or failure of the
     *    operation.
     */
    private ResultDesc doPut(String ref, String obj, boolean requireNew) {
        String failure = null;
        if (obj == null) {
            failure = "no object data given";
        } else if (requireNew) {
            failure = "requireNew option not supported in file store";
        } else {
            try {
                Writer objWriter = new OutputStreamWriter(new FileOutputStream(odbFile(ref)), UTF_8);
                objWriter.write(obj);
                objWriter.close();
            } catch (Exception e) {
                failure = e.getMessage();
            }
        }
        return new ResultDesc(ref, failure);
    }

    /**
     * Perform a single 'remove' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be deleted.
     *
     * @return a ResultDesc object describing the success or failure of the
     *    operation.
     */
    private ResultDesc doRemove(String ref) {
        String failure = null;
        try {
            odbFile(ref).delete();
        } catch (Exception e) {
            failure = e.getMessage();
        }
        return new ResultDesc(ref, failure);
    }

    /**
     * Service a 'get' request.  This is a request to retrieve one or more
     * objects from the object store.
     *
     * @param what  The objects sought.
     * @param handler  Object to receive results (i.e., the objects retrieved
     *    or failure indicators), when available.
     */
    public void getObjects(RequestDesc[] what, GetResultHandler handler) {
        List<ObjectDesc> resultList = new LinkedList<>();
        for (RequestDesc req : what) {
            resultList.addAll(doGet(req.ref()));
        }
        ObjectDesc[] results = resultList.toArray(new ObjectDesc[0]);

        if (handler != null) {
            handler.handle(results);
        }
    }

    /**
     * Generate the file containing a particular JSON object.
     *
     * @param ref  The reference string for the object.
     *
     * @return a File object for the file containing JSON for 'ref'.
     */
    private File odbFile(String ref) {
        return new File(myODBDirectory, ref + ".json");
    }

    /**
     * Service a 'put' request.  This is a request to write one or more objects
     * to the object store.
     *
     * @param what  The objects to be written.
     * @param handler  Object to receive results (i.e., operation success or
     *    failure indicators), when available.
     */
    public void putObjects(PutDesc[] what, RequestResultHandler handler) {
        ResultDesc[] results = new ResultDesc[what.length];
        for (int i = 0; i < what.length; ++i) {
            results[i] =
                doPut(what[i].ref(), what[i].obj(), what[i].isRequireNew());
        }
        if (handler != null) {
            handler.handle(results);
        }
    }

    /**
     * Service a 'query' request.  This is a request to query one or more
     * objects from the store.
     *
     * @param what  Query templates for the objects sought.
     * @param handler  Object to receive results (i.e., the objects retrieved
     *    or failure indicators), when available.
     */
    public void queryObjects(QueryDesc[] what, GetResultHandler handler) {
        throw new UnsupportedOperationException(
            "FileObjectStore can't do a query");
    }

    /**
     * Service a 'remove' request.  This is a request to delete one or more
     * objects from the object store.
     *
     * @param what  The objects to be removed.
     * @param handler  Object to receive results (i.e., operation success or
     *    failure indicators), when available.
     */
    public void removeObjects(RequestDesc[] what,
                              RequestResultHandler handler) {
        ResultDesc[] results = new ResultDesc[what.length];
        for (int i = 0; i < what.length; ++i) {
            results[i] = doRemove(what[i].ref());
        }
        if (handler != null) {
            handler.handle(results);
        }
    }

    /**
     * Do any work required immediately prior to shutting down the server.
     * This method gets invoked at most once, at server shutdown time.
     */
    public void shutdown() {
        /* nothing to do in this implementation */
    }

    /**
     * Service an 'update' request.  This is a request to write one or more
     * objects to the store, subject to a version number check to assure
     * atomicity.
     *
     * @param what  The objects to be written.
     * @param handler  Object to receive results (i.e., operation success or
     *    failure indicators), when available.
     */
    public void updateObjects(UpdateDesc[] what, RequestResultHandler handler)
    {
        throw new UnsupportedOperationException(
            "FileObjectStore can't do an update");
    }
}