package org.elkoserver.objectdatabase.store.mongostore

import com.grack.nanojson.JsonParserException
import com.mongodb.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.UpdateOptions
import org.bson.Document
import org.bson.types.ObjectId
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.json.*
import org.elkoserver.objectdatabase.store.*
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.LinkedList

/**
 * An [ObjectStore] implementation that stores objects in a MongoDB NoSQL
 * object database.
 *
 * Constructor.  Currently there is nothing to do, since all the real
 * initialization work happens in [initialize()][.initialize].
 */
class MongoObjectStore : ObjectStore {
    /** The Mongo database we are using  */
    private lateinit var myDatabase: MongoDatabase

    /** The default Mongo collection holding the normal objects  */
    private lateinit var myObjectDatabaseCollection: MongoCollection<Document>

    /**
     * Do the initialization required to begin providing object store
     * services.
     *
     * The property `"*propRoot*.odjdb.mongo.hostport"` should
     * specify the address of the MongoDB server holding the objects.
     *
     * The optional property `"*propRoot*.odjdb.mongo.dbname"`
     * allows the Mongo database name to be specified.  If omitted, this
     * defaults to `"elko"`.
     *
     * The optional property `"*propRoot*.odjdb.mongo.collname"`
     * allows the collection containing the object repository to be specified.
     * If omitted, this defaults to `"odb"`.
     *
     * @param props  Properties describing configuration information.
     * @param propRoot  Prefix string for selecting relevant properties.
     */
    override fun initialize(props: ElkoProperties, propRoot: String, gorgel: Gorgel) {
        val mongoPropRoot = "$propRoot.odjdb.mongo"
        val addressStr = props.getProperty("$mongoPropRoot.hostport")
                ?: throw IllegalStateException("no mongo database server address specified")
        val colon = addressStr.indexOf(':')
        val port: Int
        val host: String
        if (colon < 0) {
            port = 27017
            host = addressStr
        } else {
            port = addressStr.substring(colon + 1).toInt()
            host = addressStr.take(colon)
        }
        /* The MongoDB instance in which the objects are stored. */
        val myMongo = MongoClient(host, port)
        val dbName = props.getProperty("$mongoPropRoot.dbname", "elko")
        myDatabase = myMongo.getDatabase(dbName)
        val collName = props.getProperty("$mongoPropRoot.collname", "odb")
        myObjectDatabaseCollection = myDatabase.getCollection(collName)
    }

    /**
     * Obtain the object or objects that a field value references.
     *
     * @param value  The value to dereference.
     * @param collection   The collection to fetch from.
     * @param results  List in which to place the object or objects obtained.
     */
    private fun dereferenceValue(value: Any, collection: MongoCollection<Document>, results: MutableList<ObjectDesc>) {
        if (value is JsonArray) {
            value
                    .filterIsInstance<String>()
                    .forEach { results.addAll(doGet(it, collection)) }
        } else if (value is String) {
            results.addAll(doGet(value, collection))
        }
    }

    /**
     * Perform a single 'get' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be gotten.
     * @param collection  Collection to get from.
     *
     * @return a list of ObjectDesc objects, the first of which will be
     * the result of getting 'ref' and the remainder, if any, will be the
     * results of getting any contents objects.
     */
    private fun doGet(ref: String, collection: MongoCollection<Document>): List<ObjectDesc> {
        val results: MutableList<ObjectDesc> = LinkedList()
        var failure: String? = null
        var obj: String? = null
        var contents: List<ObjectDesc>? = null
        try {
            val query = Document()
            query["ref"] = ref
            val dbObj = collection.find(query).first()
            if (dbObj != null) {
                val jsonObj = dbObjectToJSONObject(dbObj)
                obj = JsonObjectSerialization.sendableString(jsonObj)
                contents = doGetContents(jsonObj, collection)
            } else {
                failure = "not found"
            }
        } catch (e: Exception) {
            obj = null
            failure = e.message
        }
        results.add(ObjectDesc(ref, obj, failure))
        if (contents != null) {
            results.addAll(contents)
        }
        return results
    }

    private fun dbObjectToJSONObject(dbObj: Document): JsonObject {
        val result = JsonObject()
        for (key in dbObj.keys) {
            if (!key.startsWith("_")) {
                var value = dbObj[key]
                if (value is List<*>) {
                    value = dbListToJSONArray(value)
                } else if (value is Document) {
                    value = dbObjectToJSONObject(value)
                }
                result.put(key, value)
            } else if (key == "_id") {
                val oid = dbObj[key] as ObjectId
                result.put(key, oid.toString())
            }
        }
        return result
    }

    private fun dbListToJSONArray(dbList: List<*>): JsonArray {
        val result = JsonArray()
        dbList
                .map {
                    when (it) {
                        is List<*> -> dbListToJSONArray(it)
                        is Document -> dbObjectToJSONObject(it)
                        else -> it
                    }
                }
                .forEach(result::add)
        return result
    }

    private fun jsonLiteralToDBObject(objStr: String, ref: String): Document? {
        val obj: JsonObject = try {
            JsonParsing.jsonObjectFromString(objStr)!!
        } catch (e: JsonParserException) {
            return null
        }
        val result = jsonObjectToDBObject(obj)
        result["ref"] = ref

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
        val mods = obj.getArray("mods", null)
        mods.iterator().forEachRemaining { mod: Any? ->
            val elkoModAsObject = JsonWrapping.wrapWithElkoJsonImplementationIfNeeded(mod)
            if (elkoModAsObject is JsonObject) {
                if ("geopos" == elkoModAsObject.getStringOrNull("type")) {
                    val lat = elkoModAsObject.getDouble("lat", 0.0)
                    val lon = elkoModAsObject.getDouble("lon", 0.0)
                    val qpos = Document()
                    qpos["lat"] = lat
                    qpos["lon"] = lon
                    result["_qpos_"] = qpos
                }
            }
        }
        // End of ugly modularity boundary violation
        return result
    }

    private fun valueToDBValue(value: Any?) =
            if (value is JsonObject) {
                jsonObjectToDBObject(value)
            } else if (value is JsonArray) {
                jsonArrayToDBArray(value)
            } else if (value is Long) {
                if (Int.MIN_VALUE <= value && value <= Int.MAX_VALUE) {
                    value.toInt()
                } else {
                    value
                }
            } else {
                value
            }

    private fun jsonArrayToDBArray(arr: JsonArray): ArrayList<Any?> {
        val result = ArrayList<Any?>(arr.size())
        arr.mapTo(result, ::valueToDBValue)
        return result
    }

    private fun jsonObjectToDBObject(obj: JsonObject): Document {
        val result = Document()
        for ((key, value) in obj.entrySet()) {
            result[key] = valueToDBValue(value)
        }
        return result
    }

    /**
     * Fetch the contents of an object.
     *
     * @param obj  The object whose contents are sought.
     *
     * @return a List of ObjectDesc objects for the contents as
     * requested.
     */
    private fun doGetContents(obj: JsonObject, collection: MongoCollection<Document>): List<ObjectDesc> {
        val results: MutableList<ObjectDesc> = LinkedList()
        for ((propName, value) in obj.entrySet()) {
            if (propName.startsWith("ref$")) {
                dereferenceValue(value, collection, results)
            }
        }
        return results
    }

    /**
     * Perform a single 'put' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be written.
     * @param obj  JSON string encoding the object to be written.
     * @param collection  Collection to put to.
     *
     * @return a ResultDesc object describing the success or failure of the
     * operation.
     */
    private fun doPut(ref: String, obj: String, collection: MongoCollection<Document>, requireNew: Boolean): ResultDesc {
        var failure: String? = null
        try {
            val objectToWrite = jsonLiteralToDBObject(obj, ref) ?: throw IllegalStateException()
            if (requireNew) {
                collection.insertOne(objectToWrite)
            } else {
                val query = Document()
                query["ref"] = ref
                collection.updateOne(query, objectToWrite, UpdateOptions().upsert(true))
            }
        } catch (e: Exception) {
            failure = e.message
        }
        return ResultDesc(ref, failure)
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
     * the operation.
     */
    private fun doUpdate(ref: String, version: Int, obj: String, collection: MongoCollection<Document>): UpdateResultDesc {
        var failure: String? = null
        var atomicFailure = false
        try {
            val objectToWrite = jsonLiteralToDBObject(obj, ref) ?: throw IllegalStateException()
            val query = Document()
            query["ref"] = ref
            query["version"] = version
            val result = collection.updateOne(query, objectToWrite, UpdateOptions().upsert(false))
            if (result.matchedCount != 1L) {
                failure = "stale version number on update"
                atomicFailure = true
            }
        } catch (e: Exception) {
            failure = e.message
        }
        return UpdateResultDesc(ref, failure, atomicFailure)
    }

    /**
     * Perform a single 'remove' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be deleted.
     * @param collection  Collection to remove from.
     *
     * @return a ResultDesc object describing the success or failure of the
     * operation.
     */
    private fun doRemove(ref: String, collection: MongoCollection<Document>): ResultDesc {
        var failure: String? = null
        try {
            val query = Document()
            query["ref"] = ref
            collection.deleteOne(query)
        } catch (e: Exception) {
            failure = e.message
        }
        return ResultDesc(ref, failure)
    }

    /**
     * Service a 'get' request.  This is a request to retrieve one or more
     * objects from the object store.
     *
     * @param what  The objects sought.
     * @param handler  Object to receive results (i.e., the objects retrieved
     * or failure indicators), when available.
     */
    override fun getObjects(what: Array<RequestDesc>, handler: GetResultHandler) {
        val resultList: MutableList<ObjectDesc> = LinkedList()
        for (req in what) {
            resultList.addAll(doGet(req.ref, getCollection(req.collectionName)))
        }
        handler.handle(resultList.toTypedArray())
    }

    /**
     * Service a 'put' request.  This is a request to write one or more objects
     * to the object store.
     *
     * @param what  The objects to be written.
     * @param handler  Object to receive results (i.e., operation success or
     * failure indicators), when available.
     */
    override fun putObjects(what: Array<PutDesc>, handler: RequestResultHandler) {
        val results = Array(what.size) {
            val collection = getCollection(what[it].collectionName)
            doPut(what[it].ref, what[it].obj, collection, what[it].isRequireNew)
        }
        handler.handle(results)
    }

    /**
     * Service an 'update' request.  This is a request to write one or more
     * objects to the store, subject to a version number check to assure
     * atomicity.
     *
     * @param what  The objects to be written.
     * @param handler  Object to receive results (i.e., operation success or
     * failure indicators), when available.
     */
    override fun updateObjects(what: Array<UpdateDesc>, handler: RequestResultHandler) {
        val results = Array(what.size) {
            val collection = getCollection(what[it].collectionName)
            doUpdate(what[it].ref, what[it].version, what[it].obj, collection)
        }
        handler.handle(results)
    }

    /**
     * Perform a single 'query' operation on the local object store.
     *
     * @param template  Query template indicating what objects are sought.
     * @param collection  Collection to query.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     * indicate no fixed limit.
     *
     * @return a list of ObjectDesc objects for objects matching the query.
     */
    private fun doQuery(template: JsonObject, collection: MongoCollection<Document>, maxResults: Int): List<ObjectDesc> {
        val results: MutableList<ObjectDesc> = LinkedList()
        try {
            val query = jsonObjectToDBObject(template)
            val cursor = if (maxResults > 0) {
                collection.find(query).batchSize(-maxResults)
            } else {
                collection.find(query)
            }
            cursor
                    .map(::dbObjectToJSONObject)
                    .map(JsonObjectSerialization::sendableString)
                    .mapTo(results) { ObjectDesc("query", it, null) }
        } catch (e: Exception) {
            results.add(ObjectDesc("query", null, e.message))
        }
        return results
    }

    /**
     * Map from a collection name to a Mongo collection object.
     *
     * @param collectionName  Name of the collection desired, or null to get
     * the configured default (whatever that may be).
     *
     * @return the DBCollection object corresponding to collectionName.
     */
    private fun getCollection(collectionName: String?) =
            if (collectionName == null) {
                myObjectDatabaseCollection
            } else {
                myDatabase.getCollection(collectionName)
            }

    /**
     * Service a 'query' request.  This is a request to query one or more
     * objects from the store.
     *
     * @param what  Query templates for the objects sought.
     * @param handler  Object to receive results (i.e., the objects retrieved
     * or failure indicators), when available.
     */
    override fun queryObjects(what: Array<QueryDesc>, handler: GetResultHandler) {
        val resultList: MutableList<ObjectDesc> = LinkedList()
        for (req in what) {
            val collection = getCollection(req.collectionName)
            resultList.addAll(doQuery(req.template, collection, req.maxResults))
        }
        handler.handle(resultList.toTypedArray())
    }

    /**
     * Service a 'remove' request.  This is a request to delete one or more
     * objects from the object store.
     *
     * @param what  The objects to be removed.
     * @param handler  Object to receive results (i.e., operation success or
     * failure indicators), when available.
     */
    override fun removeObjects(what: Array<RequestDesc>, handler: RequestResultHandler) {
        val results = Array(what.size) { doRemove(what[it].ref, getCollection(what[it].collectionName)) }
        handler.handle(results)
    }

    /**
     * Do any work required immediately prior to shutting down the server.
     * This method gets invoked at most once, at server shutdown time.
     */
    override fun shutdown() {
        /* nothing to do in this implementation */
    }
}