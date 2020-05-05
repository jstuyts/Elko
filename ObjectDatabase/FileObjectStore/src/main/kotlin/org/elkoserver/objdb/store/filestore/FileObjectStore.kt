package org.elkoserver.objdb.store.filestore

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.json.JsonArray
import org.elkoserver.json.JsonObject
import org.elkoserver.json.JsonParsing
import org.elkoserver.objdb.store.GetResultHandler
import org.elkoserver.objdb.store.ObjectDesc
import org.elkoserver.objdb.store.ObjectStore
import org.elkoserver.objdb.store.PutDesc
import org.elkoserver.objdb.store.QueryDesc
import org.elkoserver.objdb.store.RequestDesc
import org.elkoserver.objdb.store.RequestResultHandler
import org.elkoserver.objdb.store.ResultDesc
import org.elkoserver.objdb.store.UpdateDesc
import org.elkoserver.util.trace.Trace
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.LinkedList

/**
 * A simple [ObjectStore] implementation that stores objects in text
 * files, one file per object.  Each file contains a JSON-encoded
 * representation of the object it stores.
 *
 * Constructor.  Currently there is nothing to do, since all the real
 * initialization work happens in [initialize()][.initialize].
 */
class FileObjectStore : ObjectStore {
    /** The directory in which the object "database" contents are stored.  */
    private lateinit var myODBDirectory: File

    /**
     * Do the initialization required to begin providing object store
     * services.
     *
     *
     * The property `"*propRoot*.odb"` should specify the
     * pathname of the directory in which the object description files are
     * stored.
     *
     * @param props  Properties describing configuration information.
     * @param propRoot  Prefix string for selecting relevant properties.
     * @param appTrace  Trace object for use in logging.
     */
    override fun initialize(props: ElkoProperties, propRoot: String, appTrace: Trace) {
        val dirname = props.getProperty("$propRoot.odb") ?: appTrace.fatalError("no object database directory specified")
        myODBDirectory = File(dirname)
        if (!myODBDirectory.exists()) {
            appTrace.fatalError("object database directory '" + dirname +
                    "' does not exist")
        } else if (!myODBDirectory.isDirectory) {
            appTrace.fatalError("requested object database directory " + dirname +
                    " is not a directory")
        }
    }

    /**
     * Obtain the object or objects that a field value references.
     *
     * @param value  The value to dereference.
     * @param results  List in which to place the object or objects obtained.
     */
    private fun dereferenceValue(value: Any, results: MutableList<ObjectDesc>) {
        if (value is JsonArray) {
            for (elem in value) {
                if (elem is String) {
                    results.addAll(doGet(elem))
                }
            }
        } else if (value is String) {
            results.addAll(doGet(value))
        }
    }

    /**
     * Perform a single 'get' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be gotten.
     *
     * @return a list of ObjectDesc objects, the first of which will be
     * the result of getting 'ref' and the remainder, if any, will be the
     * results of getting any contents objects.
     */
    private fun doGet(ref: String): List<ObjectDesc> {
        val results: MutableList<ObjectDesc> = LinkedList()
        var failure: String? = null
        var obj: String? = null
        var contents: List<ObjectDesc>? = null
        try {
            val file = odbFile(ref)
            val length = file.length()
            if (length > 0) {
                val objReader: Reader = InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)
                val buf = CharArray(length.toInt())
                // FIXME: Handle the result
                objReader.read(buf)
                objReader.close()
                obj = String(buf)
                val jsonObj = JsonParsing.jsonObjectFromString(obj)!!
                contents = doGetContents(jsonObj)
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
    private fun doGetContents(obj: JsonObject): List<ObjectDesc> {
        val results: MutableList<ObjectDesc> = LinkedList()
        for ((propName, value) in obj.entrySet()) {
            if (propName.startsWith("ref$")) {
                dereferenceValue(value, results)
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
    private fun doPut(ref: String, obj: String?, requireNew: Boolean): ResultDesc {
        var failure: String? = null
        when {
            obj == null -> failure = "no object data given"
            requireNew -> failure = "requireNew option not supported in file store"
            else ->
                try {
                    val objWriter: Writer = OutputStreamWriter(FileOutputStream(odbFile(ref)), StandardCharsets.UTF_8)
                    objWriter.write(obj)
                    objWriter.close()
                } catch (e: Exception) {
                    failure = e.message
                }
        }
        return ResultDesc(ref, failure)
    }

    /**
     * Perform a single 'remove' operation on the local object store.
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
        val resultList: MutableList<ObjectDesc> = LinkedList()
        for (req in what) {
            resultList.addAll(doGet(req.ref()))
        }
        handler.handle(resultList.toTypedArray())
    }

    /**
     * Generate the file containing a particular JSON object.
     *
     * @param ref  The reference string for the object.
     *
     * @return a File object for the file containing JSON for 'ref'.
     */
    private fun odbFile(ref: String): File {
        return File(myODBDirectory, "$ref.json")
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
        val results = Array(what.size) { doPut(what[it].ref(), what[it].obj(), what[it].isRequireNew)}
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
        val results = Array(what.size) { doRemove(what[it].ref()) }
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
}