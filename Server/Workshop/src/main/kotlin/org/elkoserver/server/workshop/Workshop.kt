package org.elkoserver.server.workshop

import com.grack.nanojson.JsonObject
import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.json.Encodable
import org.elkoserver.objectdatabase.ObjectDatabase
import org.elkoserver.util.tokenize
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.LinkedList
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
 * @param myObjectDatabase  Database for persistent object storage.
 * @param myServer  Server object.
 */
class Workshop internal constructor(
    private val myObjectDatabase: ObjectDatabase,
    private val myServer: Server,
    internal val refTable: RefTable,
    private val gorgel: Gorgel,
    private val startupWorkerListGorgel: Gorgel,
    baseCommGorgel: Gorgel,
    // FIXME: Only inject object databases into workers for now. Should switch to more generic injection, so workers are not limited to small set of possible dependency types.
    private val workerDatabases: Map<String, ObjectDatabase>
) {

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
        myObjectDatabase.addClass("workers", StartupWorkerList::class.java)
        myObjectDatabase.getObject("workers", StartupWorkerListReceiver("workers"))
        workerListRefs?.tokenize(' ', ',', ';', ':')?.forEach { tag ->
            myObjectDatabase.getObject(tag, StartupWorkerListReceiver(tag))
        }
    }

    private inner class StartupWorkerListReceiver(var myTag: String) : Consumer<Any?> {
        override fun accept(obj: Any?) {
            val workers = obj as StartupWorkerList?
            if (workers != null) {
                gorgel.i?.run { info("loading startup worker list '$myTag'") }
                workers.fetchFromObjectDatabase(myObjectDatabase, this@Workshop, startupWorkerListGorgel)
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
        myObjectDatabase.getObject(ref, handler)
    }

    /**
     * Fetch an object from a particular worker database.
     *
     * @param ref  The ref of the object desired.
     * @param databaseId  The ID of the worker database to get the object from.
     * @param handler  Callback that will be invoked with the object in
     * question, or null if the object was not available.
     */
    fun getObject(ref: String, databaseId: String, handler: Consumer<Any?>) {
        workerDatabases.getValue(databaseId).getObject(ref, handler)
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
        myObjectDatabase.queryObjects(query, maxResults, handler)
    }

    /**
     * Query a particular worker database.
     *
     * @param query  JSON object containing a MongoDB query structure.
     * @param databaseId  The ID of the worker database to query for objects.
     * @param maxResults  Maximum number of result objects acceptable; a value
     * of 0 means no limit.
     * @param handler  Callback that will be invoked with a results array, or
     * null if the query failed.
     */
    fun queryObjects(query: JsonObject, databaseId: String, maxResults: Int, handler: Consumer<Any?>) {
        workerDatabases.getValue(databaseId).queryObjects(query, maxResults, handler)
    }

    /**
     * Store an object into the repository.
     *
     * @param ref  Ref of the object to write.
     * @param object  The object itself.
     */
    fun putObject(ref: String, `object`: Encodable) {
        myObjectDatabase.putObject(ref, `object`, null)
    }

    /**
     * Store an object into a particular worker database with
     * results notification.
     *
     * @param ref  Ref of the object to write.
     * @param object  The object itself.
     * @param databaseId  The ID of the worker database to save the object in.
     * @param resultHandler  Handler that wil be invoked with the result of
     * the operation; the result will be null if the operation succeeded, or
     * an error string if the operation failed.
     */
    fun putObject(ref: String, `object`: Encodable, databaseId: String, resultHandler: Consumer<Any?>?) {
        workerDatabases.getValue(databaseId).putObject(ref, `object`, resultHandler)
    }

    /**
     * Update the state of an object in the repository.
     *
     * @param ref  Ref of the object to write.
     * @param version  Version number of the instance being replaced
     * @param object  The object itself.
     * @param resultHandler  Handler that wil be invoked with the result of
     * the operation; the result will be null if the operation succeeded, or
     * an error string if the operation failed.
     */
    fun updateObject(ref: String, version: Int, `object`: Encodable, resultHandler: Consumer<Any?>) {
        myObjectDatabase.updateObject(ref, version, `object`, resultHandler)
    }

    /**
     * Update the state of an object in some worker database.
     *
     * @param ref  Ref of the object to write.
     * @param version  Version number of the instance being replaced
     * @param object  The object itself.
     * @param databaseId  The ID of the worker database to update the object in.
     * @param resultHandler  Handler that wil be invoked with the result of
     * the operation; the result will be null if the operation succeeded, or
     * an error string if the operation failed.
     */
    fun updateObject(
        ref: String,
        version: Int,
        `object`: Encodable,
        databaseId: String,
        resultHandler: Consumer<Any?>?
    ) {
        workerDatabases.getValue(databaseId).updateObject(ref, version, `object`, resultHandler)
    }

    /**
     * Delete an object from the repository.
     *
     * @param ref  The ref of the object to be deleted.
     */
    fun removeObject(ref: String) {
        myObjectDatabase.removeObject(ref, null)
    }

    init {
        myObjectDatabase.addClass("auth", AuthDesc::class.java)
        refTable.addRef(ClientHandler(this, baseCommGorgel.getChild(ClientHandler::class)))
        refTable.addRef(AdminHandler(this, baseCommGorgel.getChild(AdminHandler::class)))
        isShuttingDown = false
        myServer.registerShutdownWatcher {
            isShuttingDown = true
            myObjectDatabase.shutdown()
        }
    }
}