package org.elkoserver.objdb

import com.grack.nanojson.JsonParserException
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.foundation.run.Runner.Companion.currentRunner
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JsonObject
import org.elkoserver.json.JsonParsing.jsonObjectFromString
import org.elkoserver.objdb.ObjectStoreFactory.createAndInitializeObjectStore
import org.elkoserver.objdb.store.GetResultHandler
import org.elkoserver.objdb.store.ObjectDesc
import org.elkoserver.objdb.store.ObjectStore
import org.elkoserver.objdb.store.PutDesc
import org.elkoserver.objdb.store.QueryDesc
import org.elkoserver.objdb.store.RequestDesc
import org.elkoserver.objdb.store.RequestResultHandler
import org.elkoserver.objdb.store.ResultDesc
import org.elkoserver.objdb.store.UpdateDesc
import org.elkoserver.objdb.store.UpdateResultDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.time.Clock
import java.util.function.Consumer

/**
 * Asynchronous access to a local instance of the object database.  This is
 * implemented as a separate run queue thread synchronously accessing a local
 * object store.
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
class ObjDBLocal(props: ElkoProperties, propRoot: String, appTrace: Trace, traceFactory: TraceFactory, clock: Clock) : ObjDBBase(appTrace, traceFactory, clock) {
    /** Local object storage module.  */
    private val myObjectStore: ObjectStore = createAndInitializeObjectStore(props, propRoot, tr)

    /** Async run queue for giving tasks to the ODB thread.  */
    private val myRunner: Runner = Runner("Elko RunQueue LocalObjDB", traceFactory)

    /** Async run queue for giving results back to the main thread.  */
    private val myReturnRunner = currentRunner(traceFactory)

    /**
     * Fetch an object from the store.
     *
     * @param ref  Reference string naming the object desired.
     * @param collectionName  Name of collection to get from, or null to take
     * the configured default (or the db doesn't use this abstraction).
     * @param handler  Handler to be called with the result.  The result will
     * be the object requested, or null if the object could not be
     * retrieved.
     */
    override fun getObject(ref: String, collectionName: String?, handler: Consumer<Any?>) {
        myRunner.enqueue(GetCallHandler(ref, collectionName, handler))
    }

    /**
     * Handler to call the store's 'get' method.  Runs in the ODB thread.
     */
    private inner class GetCallHandler internal constructor(private val myRef: String, private val myCollectionName: String?, private val myRunnable: Consumer<Any?>) : Runnable, GetResultHandler {
        override fun run() {
            val what = arrayOf(RequestDesc(myRef, myCollectionName, true))
            myObjectStore.getObjects(what, this)
        }

        override fun handle(results: Array<ObjectDesc>?) {
            var obj: Any? = null
            if (results != null) {
                val failure = results[0].failure()
                obj = if (failure == null) {
                    decodeObject(myRef, results)
                } else {
                    tr.errorm("object store error getting $myRef: $failure")
                    null
                }
            }
            myReturnRunner.enqueue(ArgRunnableRunnable(myRunnable, obj))
        }
    }

    /**
     * Store an object into the store.
     *
     * @param ref  Reference string naming the object to be stored.
     * @param obj  The object to be stored.
     * @param collectionName  Name of collection to put into, or null to take
     * the configured default (or the db doesn't use this abstraction).
     * @param requireNew  If true, require that the object with the given ref
     * not already exist
     * @param handler  Handler to be called with the result.  The result will
     * be a status indicator: an error message string if there was an error,
     * or null if the operation was successful.
     */
    override fun putObject(ref: String, obj: Encodable, collectionName: String?, requireNew: Boolean, handler: Consumer<Any?>?) {
        val objToWrite = obj.encode(EncodeControl.forRepository)!!
        myRunner.enqueue(PutCallHandler(ref, objToWrite, collectionName, requireNew, handler))
    }

    /**
     * Update an object in the store.
     *
     * @param ref  Reference string naming the object to be stored.
     * @param version  Version number of the object to be updated
     * @param obj  The object to be stored.
     * @param collectionName  Name of collection to put into, or null to take
     * the configured default (or the db doesn't use this abstraction).
     * @param handler  Handler to be called with the result.  The result will
     * be a status indicator: an error message string if there was an error,
     * or null if the operation was successful.
     */
    override fun updateObject(ref: String, version: Int, obj: Encodable, collectionName: String?, handler: Consumer<Any?>?) {
        val objToWrite = obj.encode(EncodeControl.forRepository)!!
        myRunner.enqueue(UpdateCallHandler(ref, version, objToWrite, collectionName, handler))
    }

    /**
     * Handler to call the store's 'put' method.  Runs in the ODB thread.
     */
    private inner class PutCallHandler internal constructor(private val myRef: String, private val myObj: JSONLiteral, private val myCollectionName: String?, private val amRequireNew: Boolean, private val myRunnable: Consumer<Any?>?) : Runnable, RequestResultHandler {
        override fun run() {
            val what = arrayOf(PutDesc(myRef, myObj.sendableString(), myCollectionName, amRequireNew))
            myObjectStore.putObjects(what, this)
        }

        override fun handle(results: Array<out ResultDesc>) {
            if (myRunnable != null) {
                myReturnRunner.enqueue(ArgRunnableRunnable(myRunnable, results[0].failure()))
            }
        }
    }

    /**
     * Handler to call the store's 'update' method.  Runs in the ODB thread.
     */
    private inner class UpdateCallHandler internal constructor(private val myRef: String, private val myVersion: Int, private val myObj: JSONLiteral,
                                                               private val myCollectionName: String?, private val myRunnable: Consumer<Any?>?) : Runnable, RequestResultHandler {
        override fun run() {
            val what = arrayOf(UpdateDesc(myRef, myVersion, myObj.sendableString(), myCollectionName))
            myObjectStore.updateObjects(what, this)
        }

        override fun handle(results: Array<out ResultDesc>) {
            if (myRunnable != null) {
                val realResult = results[0] as UpdateResultDesc
                var failure = realResult.failure()
                if (realResult.isAtomicFailure) {
                    // XXX This is an egregious hack. We should refactor the
                    // error handling path to pass a generic result object all
                    // the way back instead of just passing a string and then
                    // overloading it in this horrible, icky way
                    //
                    // Only used in class "Bank" for now.
                    failure = "@$failure"
                }
                myReturnRunner.enqueue(ArgRunnableRunnable(myRunnable, failure))
            }
        }
    }

    /**
     * Query the object store.
     *
     * @param template  Query template indicating the object(s) desired.
     * @param collectionName  Name of collection to query, or null to take the
     * configured default.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     * indicate no fixed limit.
     * @param handler  Handler to be called with the results.  The results will
     * be an array of the object(s) requested, or null if no objects could
     * be retrieved.
     */
    override fun queryObjects(template: JsonObject, collectionName: String?, maxResults: Int, handler: Consumer<Any?>) {
        myRunner.enqueue(QueryCallHandler(template, collectionName, maxResults, handler))
    }

    /**
     * Handler to call the store's 'query' method.  Runs in the ODB thread.
     */
    private inner class QueryCallHandler internal constructor(private val myTemplate: JsonObject, private val myCollectionName: String?,
                                                              private val myMaxResults: Int, private val myRunnable: Consumer<Any?>) : Runnable, GetResultHandler {
        override fun run() {
            val what = arrayOf(QueryDesc(myTemplate, myCollectionName, myMaxResults))
            myObjectStore.queryObjects(what, this)
        }

        override fun handle(results: Array<ObjectDesc>?) {
            var objs: Array<Any?>? = null
            if (results != null && results.isNotEmpty()) {
                val failure = results[0].failure()
                objs = if (failure == null) {
                    decodeObjectSet(results)
                } else {
                    tr.errorm("object store error getting query results: $failure")
                    null
                }
            }
            myReturnRunner.enqueue(ArgRunnableRunnable(myRunnable, objs))
        }

        private fun decodeObjectSet(descs: Array<ObjectDesc>): Array<Any?> {
            val results = arrayOfNulls<Any>(descs.size)
            for (i in descs.indices) {
                try {
                    val jsonObj = jsonObjectFromString(descs[i].obj()!!)!!
                    if (jsonObj.getString<String?>("type", null) != null) {
                        results[i] = decodeJSONObject(jsonObj)
                    } else {
                        results[i] = jsonObj
                    }
                } catch (e: JsonParserException) {
                    results[i] = null
                }
            }
            return results
        }

    }

    /**
     * Delete an object from the store.  Note that it is not considered an
     * error to attempt to remove an object that is not there; such an
     * operation always succeeds.
     *
     * @param ref  Reference string naming the object to remove.
     * @param collectionName  Name of collection to delete from, or null to
     * take the configured default (or the db doesn't use this abstraction).
     * @param handler  Handler to be called with the result.  The result will
     * be a status indicator: an error message string if there was an error,
     * or null if the operation was successful.
     */
    override fun removeObject(ref: String, collectionName: String?, handler: Consumer<Any?>?) {
        myRunner.enqueue(RemoveCallHandler(ref, collectionName, handler))
    }

    /**
     * Handler to call store's 'remove' method.  Runs in the ODB thread.
     */
    private inner class RemoveCallHandler internal constructor(private val myRef: String, private val myCollectionName: String?,
                                                               private val myRunnable: Consumer<Any?>?) : Runnable, RequestResultHandler {
        override fun run() {
            val what = arrayOf(RequestDesc(myRef, myCollectionName, true))
            myObjectStore.removeObjects(what, this)
        }

        override fun handle(results: Array<out ResultDesc>) {
            if (myRunnable != null) {
                myReturnRunner.enqueue(ArgRunnableRunnable(myRunnable, results[0].failure()))
            }
        }
    }

    /**
     * Shutdown the object database.
     */
    override fun shutdown() {
        myRunner.orderlyShutdown()
    }

    /**
     * Runnable to invoke an Consumer.  Runs in the main thread.
     */
    private class ArgRunnableRunnable internal constructor(private val myRunnable: Consumer<Any?>, private val myResult: Any?) : Runnable {
        override fun run() {
            myRunnable.accept(myResult)
        }
    }

    init {
        loadClassDesc(props.getProperty("$propRoot.classdesc"))
    }
}
