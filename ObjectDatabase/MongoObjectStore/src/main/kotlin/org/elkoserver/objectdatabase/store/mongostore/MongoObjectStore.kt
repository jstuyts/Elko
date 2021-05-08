package org.elkoserver.objectdatabase.store.mongostore

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.mongodb.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.UpdateOptions
import org.bson.Document
import org.elkoserver.json.JsonObjectSerialization
import org.elkoserver.objectdatabase.store.*
import java.util.LinkedList

/**
 * An [ObjectStore] implementation that stores objects in a MongoDB NoSQL
 * object database.
 *
 * Constructor.  Currently there is nothing to do, since all the real
 * initialization work happens in [initialize()][.initialize].
 */
@Suppress("unused")
class MongoObjectStore(arguments: ObjectStoreArguments) : ObjectStore {
    /** The Mongo database we are using  */
    private val myDatabase: MongoDatabase

    /** The default Mongo collection holding the normal objects  */
    private val myObjectDatabaseCollection: MongoCollection<Document>

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
            val mongoDocument = collection.find(query).first()
            if (mongoDocument != null) {
                val jsonObj = mongoDocumentToJsonObject(mongoDocument)
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
        for ((propName, value) in obj.entries) {
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
     *
     * @return a ResultDesc object describing the success or failure of the
     * operation.
     */
    private fun doPut(ref: String, obj: String): ResultDesc {
        var failure: String? = null
        try {
            val objectToWrite = jsonLiteralToMongoDocument(obj, ref)
            val query = Document()
            query["ref"] = ref
            myObjectDatabaseCollection.updateOne(query, objectToWrite, UpdateOptions().upsert(true))
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
     *
     * @return an UpdateResultDesc object describing the success or failure of
     * the operation.
     */
    private fun doUpdate(ref: String, version: Int, obj: String): UpdateResultDesc {
        var failure: String? = null
        var atomicFailure = false
        try {
            val objectToWrite = jsonLiteralToMongoDocument(obj, ref)
            val query = Document()
            query["ref"] = ref
            query["version"] = version
            val result = myObjectDatabaseCollection.updateOne(query, objectToWrite, UpdateOptions().upsert(false))
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
            resultList.addAll(doGet(req.ref, myObjectDatabaseCollection))
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
            doPut(what[it].ref, what[it].obj)
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
            doUpdate(what[it].ref, what[it].version, what[it].obj)
        }
        handler.handle(results)
    }

    /**
     * Perform a single 'query' operation on the local object store.
     *
     * @param template  Query template indicating what objects are sought.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     * indicate no fixed limit.
     *
     * @return a list of ObjectDesc objects for objects matching the query.
     */
    private fun doQuery(template: JsonObject, maxResults: Int): List<ObjectDesc> {
        val results: MutableList<ObjectDesc> = LinkedList()
        try {
            val query = jsonObjectToDMongoDocument(template)
            val cursor = if (maxResults > 0) {
                myObjectDatabaseCollection.find(query).batchSize(-maxResults)
            } else {
                myObjectDatabaseCollection.find(query)
            }
            cursor
                    .map(::mongoDocumentToJsonObject)
                    .map(JsonObjectSerialization::sendableString)
                    .mapTo(results) { ObjectDesc("query", it, null) }
        } catch (e: Exception) {
            results.add(ObjectDesc("query", null, e.message))
        }
        return results
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
            resultList.addAll(doQuery(req.template, req.maxResults))
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
        val results = Array(what.size) { doRemove(what[it].ref, myObjectDatabaseCollection) }
        handler.handle(results)
    }

    /**
     * Do any work required immediately prior to shutting down the server.
     * This method gets invoked at most once, at server shutdown time.
     */
    override fun shutdown() {
        /* nothing to do in this implementation */
    }


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
     */
    init {
        arguments.parse().run {
            /* The MongoDB instance in which the objects are stored. */
            val myMongo = MongoClient(host, port)
            myDatabase = myMongo.getDatabase(dbName)
            myObjectDatabaseCollection = myDatabase.getCollection(collName)
        }
    }
}
