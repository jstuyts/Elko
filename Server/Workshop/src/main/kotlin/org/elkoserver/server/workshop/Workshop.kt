package org.elkoserver.server.workshop

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.json.Encodable
import org.elkoserver.json.JsonObject
import org.elkoserver.objdb.ObjDb
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.LinkedList
import java.util.StringTokenizer
import java.util.function.Consumer

/**
 * Internal constructor.
 *
 * This constructor exists only because of a Java limitation: it needs to
 * create the object database object and then both save it in an instance
 * variable AND pass it to the superclass constructor.  However, Java
 * requires that the first statement in a constructor MUST be a call to
 * the superclass constructor or to another constructor of the same class.
 * It is possible to create the database and pass it to the superclass
 * constructor or to save it in an instance variable, but not both.  To get
 * around this, the public constructor creates the database object in a
 * parameter expression in a call to this internal constructor, which will
 * then possess it in a parameter variable whence it can be both passed to
 * the superclass constructor and saved in an instance variable.
 *
 * @param myObjDb  Database for persistent object storage.
 * @param myServer  Server object.
 */
class Workshop internal constructor(
        private val myObjDb: ObjDb,
        private val myServer: Server,
        internal val refTable: RefTable,
        private val gorgel: Gorgel,
        private val startupWorkerListGorgel: Gorgel,
        baseCommGorgel: Gorgel) {

    /** Flag that is set once server shutdown begins.  */
    var isShuttingDown: Boolean

    /**
     * Add a worker to the object table.
     *
     * @param key  Name by which this object will be known within the server
     * @param worker  The object itself
     */
    fun addWorkerObject(key: String, worker: WorkerObject) {
        worker.activate(key, this)
    }

    /**
     * Load the statically configured worker objects.
     */
    fun loadStartupWorkers(workerListRefs: String?) {
        myObjDb.addClass("workers", StartupWorkerList::class.java)
        myObjDb.getObject("workers", null,
                StartupWorkerListReceiver("workers"))
        if (workerListRefs != null) {
            val tags = StringTokenizer(workerListRefs, " ,;:")
            while (tags.hasMoreTokens()) {
                val tag = tags.nextToken()
                myObjDb.getObject(tag, null, StartupWorkerListReceiver(tag))
            }
        }
    }

    private inner class StartupWorkerListReceiver(var myTag: String) : Consumer<Any?> {
        override fun accept(obj: Any?) {
            val workers = obj as StartupWorkerList?
            if (workers != null) {
                gorgel.i?.run { info("loading startup worker list '$myTag'") }
                workers.fetchFromObjDb(myObjDb, this@Workshop, startupWorkerListGorgel)
            } else {
                gorgel.error("unable to load startup worker list '$myTag'")
            }
        }

    }

    /**
     * Register a newly loaded workshop service with the broker.
     *
     * @param serviceName  The name of the service to register
     */
    fun registerService(serviceName: String) {
        val services = myServer.services()
        val newServices: MutableList<ServiceDesc> = services
                .filter { "workshop-service" == it.service }
                .mapTo(LinkedList()) { it.subService(serviceName) }
        for (service in newServices) {
            myServer.registerService(service)
        }
    }

    /**
     * Reinitialize the server.
     */
    fun reinit() {
        myServer.reinit()
    }

    /**
     * Shutdown the server.
     */
    fun shutdown() {
        myServer.shutdown()
    }

    /**
     * Fetch an object from the repository.
     *
     * @param ref  The ref of the object desired.
     * @param handler  Callback that will be invoked with the object in
     * question, or null if the object was not available.
     */
    fun getObject(ref: String, handler: Consumer<Any?>) {
        myObjDb.getObject(ref, null, handler)
    }

    /**
     * Fetch an object from a particular collection in the repository.
     *
     * @param ref  The ref of the object desired.
     * @param collection  The name of the collection to use.
     * @param handler  Callback that will be invoked with the object in
     * question, or null if the object was not available.
     */
    fun getObject(ref: String, collection: String?, handler: Consumer<Any?>) {
        myObjDb.getObject(ref, collection, handler)
    }

    /**
     * Query the repository.
     *
     * @param query  JSON object containing a MongoDB query structure.
     * @param maxResults  Maximum number of result objects acceptable; a value
     * of 0 means no limit.
     * @param handler  Callback that will be invoked with a results array, or
     * null if the query failed.
     */
    fun queryObjects(query: JsonObject, maxResults: Int, handler: Consumer<Any?>) {
        myObjDb.queryObjects(query, null, maxResults, handler)
    }

    /**
     * Query a particular collection in the repository.
     *
     * @param query  JSON object containing a MongoDB query structure.
     * @param collection  The collection to use.
     * @param maxResults  Maximum number of result objects acceptable; a value
     * of 0 means no limit.
     * @param handler  Callback that will be invoked with a results array, or
     * null if the query failed.
     */
    fun queryObjects(query: JsonObject, collection: String?, maxResults: Int, handler: Consumer<Any?>) {
        myObjDb.queryObjects(query, collection, maxResults, handler)
    }

    /**
     * Store an object into the repository.
     *
     * @param ref  Ref of the object to write.
     * @param object  The object itself.
     */
    fun putObject(ref: String, `object`: Encodable) {
        myObjDb.putObject(ref, `object`, null, false, null)
    }

    /**
     * Store an object into a particular collection in the repository with
     * results notification.
     *
     * @param ref  Ref of the object to write.
     * @param object  The object itself.
     * @param collection  The name of the collection to use.
     * @param resultHandler  Handler that wil be invoked with the result of
     * the operation; the result will be null if the operation suceeded, or
     * an error string if the operation failed.
     */
    fun putObject(ref: String, `object`: Encodable, collection: String?, resultHandler: Consumer<Any?>?) {
        myObjDb.putObject(ref, `object`, collection, false, resultHandler)
    }

    /**
     * Update the state of an object in the repository.
     *
     * @param ref  Ref of the object to write.
     * @param version  Version number of the instance being replaced
     * @param object  The object itself.
     * @param resultHandler  Handler that wil be invoked with the result of
     * the operation; the result will be null if the operation suceeded, or
     * an error string if the operation failed.
     */
    fun updateObject(ref: String, version: Int, `object`: Encodable, resultHandler: Consumer<Any?>) {
        myObjDb.updateObject(ref, version, `object`, null, resultHandler)
    }

    /**
     * Update the state of an object in some collection in the repository.
     *
     * @param ref  Ref of the object to write.
     * @param version  Version number of the instance being replaced
     * @param object  The object itself.
     * @param collection  The collection to use.
     * @param resultHandler  Handler that wil be invoked with the result of
     * the operation; the result will be null if the operation suceeded, or
     * an error string if the operation failed.
     */
    fun updateObject(ref: String, version: Int, `object`: Encodable, collection: String?, resultHandler: Consumer<Any?>?) {
        myObjDb.updateObject(ref, version, `object`, collection, resultHandler)
    }

    /**
     * Delete an object from the repository.
     *
     * @param ref  The ref of the object to be deleted.
     */
    fun removeObject(ref: String) {
        myObjDb.removeObject(ref, null, null)
    }

    init {
        myObjDb.addClass("auth", AuthDesc::class.java)
        refTable.addRef(ClientHandler(this, baseCommGorgel.getChild(ClientHandler::class)))
        refTable.addRef(AdminHandler(this, baseCommGorgel.getChild(AdminHandler::class)))
        isShuttingDown = false
        myServer.registerShutdownWatcher(object : ShutdownWatcher {
            override fun noteShutdown() {
                isShuttingDown = true
                myObjDb.shutdown()
            }
        })
    }
}