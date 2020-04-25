package org.elkoserver.objdb.store.mongostore;

import com.grack.nanojson.JsonParserException;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.elkoserver.foundation.properties.ElkoProperties;
import org.elkoserver.json.JsonArray;
import org.elkoserver.json.JsonObject;
import org.elkoserver.json.JsonObjectSerialization;
import org.elkoserver.json.JsonWrapping;
import org.elkoserver.objdb.store.*;
import org.elkoserver.util.trace.Trace;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.elkoserver.json.JsonParsing.jsonObjectFromString;
import static org.elkoserver.json.JsonWrapping.wrapWithElkoJsonImplementationIfNeeded;

/**
 * An {@link ObjectStore} implementation that stores objects in a MongoDB NoSQL
 * object database.
 */
public class MongoObjectStore implements ObjectStore {

    /** The Mongo database we are using */
    private MongoDatabase myDB;

    /** The default Mongo collection holding the normal objects */
    private MongoCollection<Document> myODBCollection;

    /**
     * Constructor.  Currently there is nothing to do, since all the real
     * initialization work happens in {@link #initialize initialize()}.
     */
    public MongoObjectStore() {
    }

    /**
     * Do the initialization required to begin providing object store
     * services.
     *
     * <p>The property <code>"<i>propRoot</i>.odb.mongo.hostport"</code> should
     * specify the address of the MongoDB server holding the objects.
     *
     * <p>The optional property <code>"<i>propRoot</i>.odb.mongo.dbname"</code>
     * allows the Mongo database name to be specified.  If omitted, this
     * defaults to <code>"elko"</code>.
     *
     * <p>The optional property <code>"<i>propRoot</i>.odb.mongo.collname"</code>
     * allows the collection containing the object repository to be specified.
     * If omitted, this defaults to <code>"odb"</code>.
     *
     * @param props  Properties describing configuration information.
     * @param propRoot  Prefix string for selecting relevant properties.
     * @param appTrace  Trace object for use in logging.
     */
    public void initialize(ElkoProperties props, String propRoot,
                           Trace appTrace)
    {
        /* Trace object for diagnostics. */
        propRoot = propRoot + ".odb.mongo";

        String addressStr = props.getProperty(propRoot + ".hostport");
        if (addressStr == null) {
            appTrace.fatalError("no mongo database server address specified");
            throw new IllegalStateException();
        }
        int colon = addressStr.indexOf(':');
        int port;
        String host;
        if (colon < 0) {
            port = 27017;
            host = addressStr;
        } else {
            port = Integer.parseInt(addressStr.substring(colon + 1)) ;
            host = addressStr.substring(0, colon);
        }
        /* The MongoDB instance in which the objects are stored. */
        MongoClient myMongo = new MongoClient(host, port);
        String dbName = props.getProperty(propRoot + ".dbname", "elko");
        myDB = myMongo.getDatabase(dbName);

        String collName = props.getProperty(propRoot + ".collname", "odb");
        myODBCollection = myDB.getCollection(collName);
    }

    /**
     * Obtain the object or objects that a field value references.
     *
     * @param value  The value to dereference.
     * @param collection   The collection to fetch from.
     * @param results  List in which to place the object or objects obtained.
     */
    private void dereferenceValue(Object value, MongoCollection<Document> collection,
                                  List<ObjectDesc> results) {
        if (value instanceof JsonArray) {
            for (Object elem : (JsonArray) value) {
                if (elem instanceof String) {
                    results.addAll(doGet((String) elem, collection));
                }
            }
        } else if (value instanceof String) {
            results.addAll(doGet((String) value, collection));
        }
    }

    /**
     * Perform a single 'get' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be gotten.
     * @param collection  Collection to get from.
     *
     * @return a list of ObjectDesc objects, the first of which will be
     *    the result of getting 'ref' and the remainder, if any, will be the
     *    results of getting any contents objects.
     */
    private List<ObjectDesc> doGet(String ref, MongoCollection<Document> collection) {
        List<ObjectDesc> results = new LinkedList<>();

        String failure = null;
        String obj = null;
        List<ObjectDesc> contents = null;
        try {
            Document query = new Document();
            query.put("ref", ref);
            Document dbObj = collection.find(query).first();
            if (dbObj != null) {
                JsonObject jsonObj = dbObjectToJSONObject(dbObj);
                obj = JsonObjectSerialization.sendableString(jsonObj);
                contents = doGetContents(jsonObj, collection);
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

    private JsonObject dbObjectToJSONObject(Document dbObj) {
        JsonObject result = new JsonObject();
        for (String key : dbObj.keySet()) {
            if (!key.startsWith("_")) {
                Object value = dbObj.get(key);
                if (value instanceof List) {
                    value = dbListToJSONArray((List<?>) value);
                } else if (value instanceof Document) {
                    value = dbObjectToJSONObject((Document) value);
                }
                result.put(key, value);
            } else if (key.equals("_id")) {
                ObjectId oid = (ObjectId) dbObj.get(key);
                result.put(key, oid.toString());
            }
        }
        return result;
    }

    private JsonArray dbListToJSONArray(List<?> dbList) {
        JsonArray result = new JsonArray();
        for (Object elem : dbList) {
            if (elem instanceof List) {
                elem = dbListToJSONArray((List<?>) elem);
            } else if (elem instanceof Document) {
                elem = dbObjectToJSONObject((Document) elem);
            }
            result.add(elem);
        }
        return result;
    }

    private Document jsonLiteralToDBObject(String objStr, String ref) {
        JsonObject obj;
        try {
            obj = jsonObjectFromString(objStr);
        } catch (JsonParserException e) {
            return null;
        }
        Document result = jsonObjectToDBObject(obj);
        result.put("ref", ref);

        // WARNING: the following is a rather profound and obnoxious modularity
        // boundary violation, but as ugly as it is, it appears to be the least
        // bad way to accommodate some of the limitations of Mongodb's
        // geo-indexing feature.  In order to spatially index an object,
        // Mongodb requires the 2D coordinate information to be stored in a
        // 2-element object or array property at the top level of the object to
        // be indexed. In the case of a 2-element object, the order the
        // properties appear in the JSON encoding is meaningful, which totally
        // violates the definition of JSON but that's what they did.
        // Unfortunately, the rest of our object encoding/decoding
        // infrastructure requires object-valued properties whose values are
        // polymorphic classes to contain a "type" property to indicate what
        // class they are.  Since there's no way to control the order in which
        // properties will be encoded when the object is serialized to JSON, we
        // risk having Mongodb mistake the type tag for the latitude or
        // longitude.  Even if we could overcome this, we'd still risk having
        // Mongodb mix the latitude and longitude up with each other.
        // Consequently, what we do is notice if an object being written has a
        // "pos" property of type "geopos", and if so we manually generate an
        // additional "_qpos_" property that is well formed according to
        // MongoDB's 2D coordinate encoding rules, and have Mongodb index
        // *that*.  When an object is read from the database, we strip this
        // property off again before we return the object to the application.

        JsonArray mods = obj.getArray("mods", null);
        if (mods != null) {
            mods.iterator().forEachRemaining(mod -> {
                Object elkoModAsObject = wrapWithElkoJsonImplementationIfNeeded(mod);
                if (elkoModAsObject instanceof JsonObject) {
                    JsonObject elkoMod = (JsonObject) elkoModAsObject;
                    if ("geopos".equals(elkoMod.getString("type", null))) {
                        double lat = elkoMod.getDouble("lat", 0.0);
                        double lon = elkoMod.getDouble("lon", 0.0);
                        Document qpos = new Document();
                        qpos.put("lat", lat);
                        qpos.put("lon", lon);
                        result.put("_qpos_", qpos);
                    }
                }
            });
        }
        // End of ugly modularity boundary violation

        return result;
    }

    private Object valueToDBValue(Object value) {
        if (value instanceof JsonObject) {
            value = jsonObjectToDBObject((JsonObject) value);
        } else if (value instanceof JsonArray) {
            value = jsonArrayToDBArray((JsonArray) value);
        } else if (value instanceof Long) {
            long intValue = (Long) value;
            if (Integer.MIN_VALUE <= intValue &&
                intValue <= Integer.MAX_VALUE) {
                value = (int) intValue;
            }
        }
        return value;
    }

    private ArrayList<Object> jsonArrayToDBArray(JsonArray arr) {
        ArrayList<Object> result = new ArrayList<>(arr.size());
        for (Object elem : arr) {
            result.add(valueToDBValue(elem));
        }
        return result;
    }

    private Document jsonObjectToDBObject(JsonObject obj) {
        Document result = new Document();
        for (Map.Entry<String, Object> prop : obj.entrySet()) {
            result.put(prop.getKey(), valueToDBValue(prop.getValue()));
        }
        return result;
    }

    /**
     * Fetch the contents of an object.
     *
     * @param obj  The object whose contents are sought.
     *
     * @return a List of ObjectDesc objects for the contents as
     *    requested.
     */
    private List<ObjectDesc> doGetContents(JsonObject obj,
                                           MongoCollection<Document> collection) {
        List<ObjectDesc> results = new LinkedList<>();
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            String propName = entry.getKey();
            if (propName.startsWith("ref$")) {
                dereferenceValue(entry.getValue(), collection, results);
            }
        }
        return results;
    }

    /**
     * Perform a single 'put' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be written.
     * @param obj  JSON string encoding the object to be written.
     * @param collection  Collection to put to.
     *
     * @return a ResultDesc object describing the success or failure of the
     *    operation.
     */
    private ResultDesc doPut(String ref, String obj, MongoCollection<Document> collection,
                             boolean requireNew)
    {
        String failure = null;
        if (obj == null) {
            failure = "no object data given";
        } else {
            try {
                Document objectToWrite = jsonLiteralToDBObject(obj, ref);
                if (requireNew) {
                    collection.insertOne(objectToWrite);
                } else {
                    Document query = new Document();
                    query.put("ref", ref);
                    collection.updateOne(query, objectToWrite, new UpdateOptions().upsert(true));
                }
            } catch (Exception e) {
                failure = e.getMessage();
            }
        }
        return new ResultDesc(ref, failure);
    }

    /**
     * Perform a single 'update' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be written.
     * @param version  Expected version number of object before updating.
     * @param obj  JSON string encoding the object to be written.
     * @param collection  Collection to put to.
     *
     * @return an UpdateResultDesc object describing the success or failure of
     *    the operation.
     */
    private UpdateResultDesc doUpdate(String ref, int version, String obj,
                                      MongoCollection<Document> collection)
    {
        String failure = null;
        boolean atomicFailure = false;
        if (obj == null) {
            failure = "no object data given";
        } else {
            try {
                Document objectToWrite = jsonLiteralToDBObject(obj, ref);
                Document query = new Document();
                query.put("ref", ref);
                query.put("version", version);
                UpdateResult result =
                    collection.updateOne(query, objectToWrite, new UpdateOptions().upsert(false));
                if (result.getMatchedCount() != 1) {
                    failure = "stale version number on update";
                    atomicFailure = true;
                }
            } catch (Exception e) {
                failure = e.getMessage();
            }
        }
        return new UpdateResultDesc(ref, failure, atomicFailure);
    }

    /**
     * Perform a single 'remove' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be deleted.
     * @param collection  Collection to remove from.
     *
     * @return a ResultDesc object describing the success or failure of the
     *    operation.
     */
    private ResultDesc doRemove(String ref, MongoCollection<Document> collection) {
        String failure = null;
        try {
            Document query = new Document();
            query.put("ref", ref);
            collection.deleteOne(query);
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
            resultList.addAll(doGet(req.ref(),
                                    getCollection(req.collectionName())));
        }
        ObjectDesc[] results = resultList.toArray(new ObjectDesc[0]);

        if (handler != null) {
            handler.handle(results);
        }
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
            MongoCollection<Document> collection = getCollection(what[i].collectionName());
            results[i] = doPut(what[i].ref(), what[i].obj(), collection,
                               what[i].isRequireNew());
        }
        if (handler != null) {
            handler.handle(results);
        }
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
    public void updateObjects(UpdateDesc[] what,
                              RequestResultHandler handler)
    {
        UpdateResultDesc[] results = new UpdateResultDesc[what.length];
        for (int i = 0; i < what.length; ++i) {
            MongoCollection<Document> collection = getCollection(what[i].collectionName());
            results[i] = doUpdate(what[i].ref(), what[i].version(),
                                  what[i].obj(), collection);
        }
        if (handler != null) {
            handler.handle(results);
        }
    }

    /**
     * Perform a single 'query' operation on the local object store.
     *
     * @param template  Query template indicating what objects are sought.
     * @param collection  Collection to query.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     *    indicate no fixed limit.
     *
     * @return a list of ObjectDesc objects for objects matching the query.
     */
    private List<ObjectDesc> doQuery(JsonObject template,
                                     MongoCollection<Document> collection, int maxResults) {
        List<ObjectDesc> results = new LinkedList<>();

        try {
            Document query = jsonObjectToDBObject(template);
            FindIterable<Document> cursor;
            if (maxResults > 0) {
                cursor = collection.find(query).batchSize(-maxResults);
            } else {
                cursor = collection.find(query);
            }
            for (Document dbObj : cursor) {
                JsonObject jsonObj = dbObjectToJSONObject(dbObj);
                String obj = JsonObjectSerialization.sendableString(jsonObj);
                results.add(new ObjectDesc("query", obj, null));
            }
        } catch (Exception e) {
            results.add(new ObjectDesc("query", null, e.getMessage()));
        }
        return results;
    }

    /**
     * Map from a collection name to a Mongo collection object.
     *
     * @param collectionName  Name of the collection desired, or null to get
     *    the configured default (whatever that may be).
     *
     * @return the DBCollection object corresponding to collectionName.
     */
    private MongoCollection<Document> getCollection(String collectionName) {
        if (collectionName == null) {
            return myODBCollection;
        } else {
            return myDB.getCollection(collectionName);
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
        List<ObjectDesc> resultList = new LinkedList<>();
        for (QueryDesc req : what) {
            MongoCollection<Document> collection = getCollection(req.collectionName());
            resultList.addAll(doQuery(req.template(), collection,
                                      req.maxResults()));
        }
        ObjectDesc[] results = resultList.toArray(new ObjectDesc[0]);

        if (handler != null) {
            handler.handle(results);
        }
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
            results[i] = doRemove(what[i].ref(),
                                  getCollection(what[i].collectionName()));
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
}
