package org.elkoserver.objectdatabase.store.filestore

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import org.elkoserver.json.JsonParsing
import org.elkoserver.objectdatabase.store.GetResultHandler
import org.elkoserver.objectdatabase.store.ObjectDesc
import org.elkoserver.objectdatabase.store.ObjectStore
import org.elkoserver.objectdatabase.store.ObjectStoreArguments
import org.elkoserver.objectdatabase.store.PutDesc
import org.elkoserver.objectdatabase.store.QueryDesc
import org.elkoserver.objectdatabase.store.RequestDesc
import org.elkoserver.objectdatabase.store.RequestResultHandler
import org.elkoserver.objectdatabase.store.ResultDesc
import org.elkoserver.objectdatabase.store.UpdateDesc
import java.io.File
import java.util.LinkedList

/**
 * A simple [ObjectStore] implementation that stores objects in text
 * files, one file per object.  Each file contains a JSON-encoded
 * representation of the object it stores.
 */
class FileObjectStore internal constructor(arguments: ObjectStoreArguments, private val fileOperations: FileOperations) :
    ObjectStore {
    /** The directory in which the object "database" contents are stored.  */
    private val myObjectDatabaseDirectory: File

    @Suppress("unused")
    constructor(arguments: ObjectStoreArguments) : this(arguments, RealFileOperations)

    /**
     * Obtain the object or objects that a field value references.
     *
     * @param value  The value to dereference.
     * @param results  List in which to place the object or objects obtained.
     */
    private fun dereferenceValue(value: Any?, results: MutableList<ObjectDesc>) {
        when (value) {
            is JsonArray -> value.forEach { possibleRef ->
                (possibleRef as? String)?.let { ref -> results.addAll(doGet(ref)) }
            }
            is String -> results.addAll(doGet(value))
        }
    }

    /**
     * Perform a single 'get' operation on the object store.
     *
     * @param ref  Object reference string of the object to be gotten.
     *
     * @return a list of ObjectDesc objects, the first of which will be
     * the result of getting 'ref' and the remainder, if any, will be the
     * results of getting any contents objects.
     */
    private fun doGet(ref: String) =
            try {
                val file = odbFile(ref)
                if (file.isFile && 0 < file.length()) {
                    val obj = fileOperations.read(file)
                    val objDesc = ObjectDesc(ref, obj, null)
                    val contents = doGetContents(obj)
                    ArrayList<ObjectDesc>(1 + contents.size).apply {
                        add(objDesc)
                        addAll(contents)
                    }
                } else {
                    listOf(ObjectDesc(ref, null, "not found"))
                }
            } catch (e: Exception) {
                listOf(ObjectDesc(ref, null, e.message))
            }

    private fun doGetContents(obj: String): List<ObjectDesc> =
            doGetContents(JsonParsing.jsonObjectFromString(obj) ?: throw IllegalStateException())

    /**
     * Fetch the contents of an object.
     *
     * @param obj  The object whose contents are sought.
     *
     * @return a List of ObjectDesc objects for the contents as
     * requested.
     */
    private fun doGetContents(obj: JsonObject): List<ObjectDesc> =
            LinkedList<ObjectDesc>().apply {
                for ((propName, value) in obj.entries) {
                    if (propName.startsWith("ref$")) {
                        dereferenceValue(value, this)
                    }
                }
            }

    /**
     * Perform a single 'put' operation on the object store.
     *
     * @param ref  Object reference string of the object to be written.
     * @param obj  JSON string encoding the object to be written.
     *
     * @return a ResultDesc object describing the success or failure of the
     * operation.
     */
    private fun doPut(ref: String, obj: String): ResultDesc {
        var failure: String? = null
        when {
            else ->
                try {
                    fileOperations.write(odbFile(ref), obj)
                } catch (e: Exception) {
                    failure = e.message
                }
        }
        return ResultDesc(ref, failure)
    }

    /**
     * Perform a single 'remove' operation on the object store.
     *
     * @param ref  Object reference string of the object to be deleted.
     *
     * @return a ResultDesc object describing the success or failure of the
     * operation.
     */
    private fun doRemove(ref: String): ResultDesc {
        var failure: String? = null
        try {
            odbFile(ref).delete()
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
        val retrievalResults = LinkedList<ObjectDesc>().apply {
            for (req in what) {
                addAll(doGet(req.ref))
            }
        }.toTypedArray()
        handler.handle(retrievalResults)
    }

    /**
     * Generate the file containing a particular JSON object.
     *
     * @param ref  The reference string for the object.
     *
     * @return a File object for the file containing JSON for 'ref'.
     */
    private fun odbFile(ref: String): File = File(myObjectDatabaseDirectory, "$ref.json")

    /**
     * Service a 'put' request.  This is a request to write one or more objects
     * to the object store.
     *
     * @param what  The objects to be written.
     * @param handler  Object to receive results (i.e., operation success or
     * failure indicators), when available.
     */
    override fun putObjects(what: Array<PutDesc>, handler: RequestResultHandler) {
        val results = Array(what.size) { doPut(what[it].ref, what[it].obj) }
        handler.handle(results)
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
        throw UnsupportedOperationException(
                "FileObjectStore can't do a query")
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
        val results = Array(what.size) { doRemove(what[it].ref) }
        handler.handle(results)
    }

    /**
     * Do any work required immediately prior to shutting down the server.
     * This method gets invoked at most once, at server shutdown time.
     */
    override fun shutDown() {
        /* nothing to do in this implementation */
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
        throw UnsupportedOperationException("FileObjectStore can't do an update")
    }

    /**
     * Do the initialization required to begin providing object store
     * services.
     *
     * The property `"*propRoot*.odjdb"` should specify the
     * pathname of the directory in which the object description files are
     * stored.
     */
    init {
        arguments.parse().run {
            myObjectDatabaseDirectory = dir
        }
    }
}
