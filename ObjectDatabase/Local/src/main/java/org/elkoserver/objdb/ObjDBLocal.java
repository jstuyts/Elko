package org.elkoserver.objdb;

import org.elkoserver.foundation.properties.ElkoProperties;
import org.elkoserver.foundation.run.Runner;
import org.elkoserver.json.*;
import org.elkoserver.objdb.store.*;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

import java.time.Clock;
import java.util.function.Consumer;

/**
 * Asynchronous access to a local instance of the object database.  This is
 * implemented as a separate run queue thread synchronously accessing a local
 * object store.
 */
public class ObjDBLocal extends ObjDBBase {
    /** Local object storage module. */
    private ObjectStore myObjectStore;

    /** Async run queue for giving tasks to the ODB thread. */
    private Runner myRunner;

    /** Async run queue for giving results back to the main thread. */
    private Runner myReturnRunner;

    /**
     * Create an object to access a local object store.
     *
     * <p>The property <code>"<i>propRoot</i>.objstore"</code> may specify the
     * fully qualified Java class name of the object store implementation to
     * use.  If unspecified, the default, <code>FileObjectStore</code>, will be used.
     *
     * <p>The property <code>"<i>propRoot</i>.classdesc"</code> may specify a
     * (comma-separated) list of references to class description objects to
     * read from the store at startup time.
     *
     * <p>Other properties may be interpreted as appropriate for the particular
     * object store implementation selected.
     *
     * @param props  Properties that the hosting server was configured with
     * @param propRoot  Prefix string for selecting relevant configuration
     *    properties.
     * @param appTrace  Trace object for event logging.
     */
    public ObjDBLocal(ElkoProperties props, String propRoot, Trace appTrace, TraceFactory traceFactory, Clock clock) {
        super(appTrace, traceFactory, clock);

        myObjectStore = ObjectStoreFactory.createAndInitializeObjectStore(props, propRoot, tr);

        myReturnRunner = Runner.currentRunner(traceFactory);
        myRunner = new Runner("Elko RunQueue LocalObjDB", traceFactory);

        loadClassDesc(props.getProperty(propRoot + ".classdesc"));
    }

    /**
     * Fetch an object from the store.
     *
     * @param ref  Reference string naming the object desired.
     * @param collectionName  Name of collection to get from, or null to take
     *    the configured default (or the db doesn't use this abstraction).
     * @param handler  Handler to be called with the result.  The result will
     *    be the object requested, or null if the object could not be
     *    retrieved.
     */
    public void getObject(String ref, String collectionName,
                          Consumer<Object> handler) {
        myRunner.enqueue(new GetCallHandler(ref, collectionName, handler));
    }

    /**
     * Handler to call the store's 'get' method.  Runs in the ODB thread.
     */
    private class GetCallHandler implements Runnable, GetResultHandler {
        private String myRef;
        private String myCollectionName;
        private Consumer<Object> myRunnable;
        GetCallHandler(String ref, String collectionName,
                       Consumer<Object> runnable) {
            myRef = ref;
            myCollectionName = collectionName;
            myRunnable = runnable;
        }
        public void run() {
            RequestDesc[] what =
                { new RequestDesc(myRef, myCollectionName, true) };
            myObjectStore.getObjects(what, this);
        }
        public void handle(ObjectDesc[] results) {
            Object obj = null;
            if (results != null) {
                String failure = results[0].failure();
                if (failure == null) {
                    obj = decodeObject(myRef, results);
                } else {
                    tr.errorm("object store error getting " + myRef + ": " +
                              failure);
                    obj = null;
                }
            }
            myReturnRunner.enqueue(new ArgRunnableRunnable(myRunnable, obj));
        }
    }

    /**
     * Store an object into the store.
     *
     * @param ref  Reference string naming the object to be stored.
     * @param obj  The object to be stored.
     * @param collectionName  Name of collection to put into, or null to take
     *    the configured default (or the db doesn't use this abstraction).
     * @param requireNew  If true, require that the object with the given ref
     *    not already exist
     * @param handler  Handler to be called with the result.  The result will
     *    be a status indicator: an error message string if there was an error,
     *    or null if the operation was successful.
     */
    public void putObject(String ref, Encodable obj, String collectionName,
                          boolean requireNew, Consumer<Object> handler) {
        JSONLiteral objToWrite = obj.encode(EncodeControl.forRepository);
        myRunner.enqueue(new PutCallHandler(ref, objToWrite, collectionName,
                                            requireNew, handler));
    }

    /**
     * Update an object in the store.
     *
     * @param ref  Reference string naming the object to be stored.
     * @param version  Version number of the object to be updated
     * @param obj  The object to be stored.
     * @param collectionName  Name of collection to put into, or null to take
     *    the configured default (or the db doesn't use this abstraction).
     * @param handler  Handler to be called with the result.  The result will
     *    be a status indicator: an error message string if there was an error,
     *    or null if the operation was successful.
     */
    public void updateObject(String ref, int version, Encodable obj,
                             String collectionName, Consumer<Object> handler) {
        JSONLiteral objToWrite = obj.encode(EncodeControl.forRepository);
        myRunner.enqueue(new UpdateCallHandler(ref, version, objToWrite,
                                               collectionName, handler));
    }

    /**
     * Handler to call the store's 'put' method.  Runs in the ODB thread.
     */
    private class PutCallHandler implements Runnable, RequestResultHandler {
        private String myRef;
        private JSONLiteral myObj;
        private String myCollectionName;
        private boolean amRequireNew;
        private Consumer<Object> myRunnable;
        PutCallHandler(String ref, JSONLiteral obj, String collectionName,
                       boolean requireNew, Consumer<Object> runnable) {
            myRef = ref;
            myObj = obj;
            myCollectionName = collectionName;
            myRunnable = runnable;
            amRequireNew = requireNew;
        }
        public void run() {
            PutDesc[] what = {
                new PutDesc(myRef,
                    myObj.sendableString(),
                    myCollectionName,
                    amRequireNew)
            };
            myObjectStore.putObjects(what, this);
        }
        public void handle(ResultDesc[] results) {
            if (myRunnable != null) {
                myReturnRunner.enqueue(
                        new ArgRunnableRunnable(myRunnable,
                                results[0].failure()));
            }
        }
    }

    /**
     * Handler to call the store's 'update' method.  Runs in the ODB thread.
     */
    private class UpdateCallHandler implements Runnable, RequestResultHandler {
        private String myRef;
        private int myVersion;
        private JSONLiteral myObj;
        private String myCollectionName;
        private Consumer<Object> myRunnable;
        UpdateCallHandler(String ref, int version, JSONLiteral obj,
                          String collectionName, Consumer<Object> runnable)
        {
            myRef = ref;
            myVersion = version;
            myObj = obj;
            myCollectionName = collectionName;
            myRunnable = runnable;
        }
        public void run() {
            UpdateDesc[] what = {
                new UpdateDesc(myRef, myVersion,
                    myObj.sendableString(),
                    myCollectionName)
            };
            myObjectStore.updateObjects(what, this);
        }
        public void handle(ResultDesc[] results) {
            if (myRunnable != null) {
                UpdateResultDesc realResult = (UpdateResultDesc) results[0];
                String failure = realResult.failure();
                if (realResult.isAtomicFailure()) {
                    // XXX This is an egregious hack. We should refactor the
                    // error handling path to pass a generic result object all
                    // the way back instead of just passing a string and then
                    // overloading it in this horrible, icky way
                    //
                    // Only used in class "Bank" for now.
                    failure = '@' + failure;
                }
                myReturnRunner.enqueue(
                        new ArgRunnableRunnable(myRunnable, failure));
            }
        }
    }

    /**
     * Query the object store.
     *
     * @param template  Query template indicating the object(s) desired.
     * @param collectionName  Name of collection to query, or null to take the
     *    configured default.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     *    indicate no fixed limit.
     * @param handler  Handler to be called with the results.  The results will
     *    be an array of the object(s) requested, or null if no objects could
     *    be retrieved.
     */
    public void queryObjects(JSONObject template, String collectionName,
                             int maxResults, Consumer<Object> handler) {
        myRunner.enqueue(new QueryCallHandler(template, collectionName,
                                              maxResults, handler));
    }

    /**
     * Handler to call the store's 'query' method.  Runs in the ODB thread.
     */
    private class QueryCallHandler implements Runnable, GetResultHandler {
        private JSONObject myTemplate;
        private String myCollectionName;
        private int myMaxResults;
        private Consumer<Object> myRunnable;
        QueryCallHandler(JSONObject template, String collectionName,
                         int maxResults, Consumer<Object> runnable) {
            myTemplate = template;
            myCollectionName = collectionName;
            myMaxResults = maxResults;
            myRunnable = runnable;
        }
        public void run() {
            QueryDesc[] what =
                { new QueryDesc(myTemplate, myCollectionName, myMaxResults) };
            myObjectStore.queryObjects(what, this);
        }
        public void handle(ObjectDesc[] results) {
            Object[] objs = null;
            if (results != null && results.length > 0) {
                String failure = results[0].failure();
                if (failure == null) {
                    objs = decodeObjectSet(results);
                } else {
                    tr.errorm("object store error getting query results: " +
                              failure);
                    objs = null;
                }
            }
            myReturnRunner.enqueue(new ArgRunnableRunnable(myRunnable, objs));
        }

        private Object[] decodeObjectSet(ObjectDesc[] descs) {
            Object[] results = new Object[descs.length];
            for (int i = 0; i < descs.length; ++i) {
                try {
                    Parser parser = new Parser(descs[i].obj());
                    JSONObject jsonObj = parser.parseObjectLiteral();
                    if (jsonObj.getProperty("type") != null) {
                        results[i] = ObjDBLocal.this.decodeJSONObject(jsonObj);
                    } else {
                        results[i] = jsonObj;
                    }
                } catch (SyntaxError e) {
                    results[i] = null;
                }
            }
            return results;
        }
    }

    /**
     * Delete an object from the store.  Note that it is not considered an
     * error to attempt to remove an object that is not there; such an
     * operation always succeeds.
     *
     * @param ref  Reference string naming the object to remove.
     * @param collectionName  Name of collection to delete from, or null to
     *    take the configured default (or the db doesn't use this abstraction).
     * @param handler  Handler to be called with the result.  The result will
     *    be a status indicator: an error message string if there was an error,
     *    or null if the operation was successful.
     */
    public void removeObject(String ref, String collectionName,
                             Consumer<Object> handler) {
        myRunner.enqueue(new RemoveCallHandler(ref, collectionName, handler));
    }

    /**
     * Handler to call store's 'remove' method.  Runs in the ODB thread.
     */
    private class RemoveCallHandler implements Runnable, RequestResultHandler {
        private String myRef;
        private String myCollectionName;
        private Consumer<Object> myRunnable;
        RemoveCallHandler(String ref, String collectionName,
                          Consumer<Object> runnable) {
            myRef = ref;
            myCollectionName = collectionName;
            myRunnable = runnable;
        }
        public void run() {
            RequestDesc[] what =
                { new RequestDesc(myRef, myCollectionName, true) };
            myObjectStore.removeObjects(what, this);
        }
        public void handle(ResultDesc[] results) {
            if (myRunnable != null) {
                myReturnRunner.enqueue(
                        new ArgRunnableRunnable(myRunnable,
                                results[0].failure()));
            }
        }
    }

    /**
     * Shutdown the object database.
     */
    public void shutdown() {
        myRunner.orderlyShutdown();
    }

    /**
     * Runnable to invoke an Consumer.  Runs in the main thread.
     */
    private static class ArgRunnableRunnable implements Runnable {
        private Consumer<Object> myRunnable;
        private Object myResult;
        ArgRunnableRunnable(Consumer<Object> runnable, Object result) {
            myRunnable = runnable;
            myResult = result;
        }
        public void run() {
            myRunnable.accept(myResult);
        }
    }
}
