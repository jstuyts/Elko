package org.elkoserver.objectdatabase

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParserException
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.singlethreadexecutor.SingleThreadExecutorRunner
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl.ForRepositoryEncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonParsing.jsonObjectFromString
import org.elkoserver.json.getStringOrNull
import org.elkoserver.objectdatabase.ObjectStoreFactory.createAndInitializeObjectStore
import org.elkoserver.objectdatabase.store.GetResultHandler
import org.elkoserver.objectdatabase.store.ObjectDesc
import org.elkoserver.objectdatabase.store.ObjectStore
import org.elkoserver.objectdatabase.store.PutDesc
import org.elkoserver.objectdatabase.store.QueryDesc
import org.elkoserver.objectdatabase.store.RequestDesc
import org.elkoserver.objectdatabase.store.RequestResultHandler
import org.elkoserver.objectdatabase.store.ResultDesc
import org.elkoserver.objectdatabase.store.UpdateDesc
import org.elkoserver.objectdatabase.store.UpdateResultDesc
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.concurrent.Executor
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
 */
class ObjectDatabaseDirect(props: ElkoProperties, propRoot: String, gorgel: Gorgel, baseGorgel: Gorgel, jsonToObjectDeserializer: JsonToObjectDeserializer,
                           private val myRunner: SingleThreadExecutorRunner, private val myReturnRunner: Executor) : ObjectDatabaseBase(gorgel, jsonToObjectDeserializer) {
    /** Object storage module.  */
    private val myObjectStore: ObjectStore = createAndInitializeObjectStore(props, propRoot, baseGorgel)

    /**
     * Fetch an object from the store.
     *
     * @param ref  Reference string naming the object desired.
     * @param handler  Handler to be called with the result.  The result will
     * be the object requested, or null if the object could not be
     * retrieved.
     */
    override fun getObject(ref: String, handler: Consumer<Any?>) {
        myRunner.execute(GetCallHandler(ref, handler))
    }

    /**
     * Handler to call the store's 'get' method.  Runs in the object database
     * thread.
     */
    private inner class GetCallHandler(private val myRef: String, private val myRunnable: Consumer<Any?>) : Runnable,
        GetResultHandler {
        override fun run() {
            val what = arrayOf(RequestDesc(myRef, true))
            myObjectStore.getObjects(what, this)
        }

        override fun handle(results: Array<ObjectDesc>) {
            val failure = results[0].failure
            val obj = if (failure == null) {
                decodeObject(myRef, results)
            } else {
                gorgel.error("object store error getting $myRef: $failure")
                null
            }
            myReturnRunner.execute(ArgRunnableRunnable(myRunnable, obj))
        }
    }

    /**
     * Store an object into the store.
     *
     * @param ref  Reference string naming the object to be stored.
     * @param obj  The object to be stored.
     * @param handler  Handler to be called with the result.  The result will
     * be a status indicator: an error message string if there was an error,
     * or null if the operation was successful.
     */
    override fun putObject(ref: String, obj: Encodable, handler: Consumer<Any?>?) {
        val objToWrite = obj.encode(ForRepositoryEncodeControl) ?: throw IllegalStateException()
        myRunner.execute(PutCallHandler(ref, objToWrite, handler))
    }

    /**
     * Update an object in the store.
     *
     * @param ref  Reference string naming the object to be stored.
     * @param version  Version number of the object to be updated
     * @param obj  The object to be stored.
     * @param handler  Handler to be called with the result.  The result will
     * be a status indicator: an error message string if there was an error,
     * or null if the operation was successful.
     */
    override fun updateObject(ref: String, version: Int, obj: Encodable, handler: Consumer<Any?>?) {
        val objToWrite = obj.encode(ForRepositoryEncodeControl) ?: throw IllegalStateException()
        myRunner.execute(UpdateCallHandler(ref, version, objToWrite, handler))
    }

    /**
     * Handler to call the store's 'put' method.  Runs in the object database
     * thread.
     */
    private inner class PutCallHandler(private val myRef: String, private val myObj: JsonLiteral, private val myRunnable: Consumer<Any?>?) : Runnable,
        RequestResultHandler {
        override fun run() {
            val what = arrayOf(PutDesc(myRef, myObj.sendableString()))
            myObjectStore.putObjects(what, this)
        }

        override fun handle(results: Array<out ResultDesc>) {
            if (myRunnable != null) {
                myReturnRunner.execute(ArgRunnableRunnable(myRunnable, results[0].failure))
            }
        }
    }

    /**
     * Handler to call the store's 'update' method.  Runs in the object database
     * thread.
     */
    private inner class UpdateCallHandler(private val myRef: String, private val myVersion: Int, private val myObj: JsonLiteral,
                                          private val myRunnable: Consumer<Any?>?) : Runnable, RequestResultHandler {
        override fun run() {
            val what = arrayOf(UpdateDesc(myRef, myVersion, myObj.sendableString()))
            myObjectStore.updateObjects(what, this)
        }

        override fun handle(results: Array<out ResultDesc>) {
            if (myRunnable != null) {
                val realResult = results[0] as UpdateResultDesc
                var failure = realResult.failure
                if (realResult.isAtomicFailure) {
                    // XXX This is an egregious hack. We should refactor the
                    // error handling path to pass a generic result object all
                    // the way back instead of just passing a string and then
                    // overloading it in this horrible, icky way
                    //
                    // Only used in class "Bank" for now.
                    failure = "@$failure"
                }
                myReturnRunner.execute(ArgRunnableRunnable(myRunnable, failure))
            }
        }
    }

    /**
     * Query the object store.
     *
     * @param template  Query template indicating the object(s) desired.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     * indicate no fixed limit.
     * @param handler  Handler to be called with the results.  The results will
     * be an array of the object(s) requested, or null if no objects could
     * be retrieved.
     */
    override fun queryObjects(template: JsonObject, maxResults: Int, handler: Consumer<Any?>) {
        myRunner.execute(QueryCallHandler(template, maxResults, handler))
    }

    /**
     * Handler to call the store's 'query' method.  Runs in the object database
     * thread.
     */
    private inner class QueryCallHandler(private val myTemplate: JsonObject,
                                         private val myMaxResults: Int, private val myRunnable: Consumer<Any?>) : Runnable, GetResultHandler {
        override fun run() {
            val what = arrayOf(QueryDesc(myTemplate, myMaxResults))
            myObjectStore.queryObjects(what, this)
        }

        override fun handle(results: Array<ObjectDesc>) {
            var objs: Array<Any?>? = null
            if (results.isNotEmpty()) {
                val failure = results[0].failure
                objs = if (failure == null) {
                    decodeObjectSet(results)
                } else {
                    gorgel.error("object store error getting query results: $failure")
                    null
                }
            }
            myReturnRunner.execute(ArgRunnableRunnable(myRunnable, objs))
        }

        private fun decodeObjectSet(descs: Array<ObjectDesc>): Array<Any?> {
            val results = arrayOfNulls<Any>(descs.size)
            for (i in descs.indices) {
                try {
                    val jsonObj = jsonObjectFromString(descs[i].obj ?: throw IllegalStateException())
                            ?: throw IllegalStateException()
                    if (jsonObj.getStringOrNull("type") != null) {
                        results[i] = decodeJsonObject(jsonObj)
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
     * @param handler  Handler to be called with the result.  The result will
     * be a status indicator: an error message string if there was an error,
     * or null if the operation was successful.
     */
    override fun removeObject(ref: String, handler: Consumer<Any?>?) {
        myRunner.execute(RemoveCallHandler(ref, handler))
    }

    /**
     * Handler to call store's 'remove' method.  Runs in the object database
     * thread.
     */
    private inner class RemoveCallHandler(private val myRef: String,
                                          private val myRunnable: Consumer<Any?>?) : Runnable, RequestResultHandler {
        override fun run() {
            val what = arrayOf(RequestDesc(myRef, true))
            myObjectStore.removeObjects(what, this)
        }

        override fun handle(results: Array<out ResultDesc>) {
            if (myRunnable != null) {
                myReturnRunner.execute(ArgRunnableRunnable(myRunnable, results[0].failure))
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
    private class ArgRunnableRunnable(private val myRunnable: Consumer<Any?>, private val myResult: Any?) : Runnable {
        override fun run() {
            myRunnable.accept(myResult)
        }
    }

    init {
        loadClassDesc(props.getProperty("$propRoot.classdesc"))
    }
}
