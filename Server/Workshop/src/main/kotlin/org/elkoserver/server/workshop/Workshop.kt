package org.elkoserver.server.workshop

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.json.Encodable
import org.elkoserver.json.JSONObject
import org.elkoserver.objdb.ObjDB
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.time.Clock
import java.util.*
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
 * @param odb  Database for persistent object storage.
 * @param server  Server object.
 * @param tr  Trace object for diagnostics.
 */
class Workshop private constructor(odb: ObjDB?, server: Server,
                                   private val tr: Trace, traceFactory: TraceFactory, clock: Clock) : RefTable(odb, traceFactory, clock) {
    /** Server object.  */
    private val myServer: Server

    /** Database that persistent objects are stored in.  */
    private val myODB: ObjDB

    /**
     * Test if the server is in the midst of shutdown.
     *
     * @return true if the server is trying to shutdown.
     */
    /** Flag that is set once server shutdown begins.  */
    var isShuttingDown: Boolean

    /**
     * Constructor.
     *
     * @param server  Server object.
     * @param appTrace  Trace object for diagnostics.
     */
    internal constructor(server: Server, appTrace: Trace, traceFactory: TraceFactory, clock: Clock) : this(server.openObjectDatabase("conf.workshop"), server, appTrace, traceFactory, clock) {}

    /**
     * Add a worker to the object table.
     *
     * @param key  Name by which this object will be known within the server
     * @param worker  The object itself
     */
    fun addWorkerObject(key: String?, worker: WorkerObject) {
        worker.activate(key, this)
    }

    /**
     * Obtain the application trace object for the workshop.
     *
     * @return the workshop's trace object.
     */
    fun appTrace(): Trace {
        return tr
    }

    /**
     * Load the statically configured worker objects.
     */
    fun loadStartupWorkers() {
        myODB.addClass("workers", StartupWorkerList::class.java)
        myODB.getObject("workers", null,
                StartupWorkerListReceiver("workers"))
        val workerListRefs = myServer.props().getProperty("conf.workshop.workers")
        if (workerListRefs != null) {
            val tags = StringTokenizer(workerListRefs, " ,;:")
            while (tags.hasMoreTokens()) {
                val tag = tags.nextToken()
                myODB.getObject(tag, null, StartupWorkerListReceiver(tag))
            }
        }
    }

    private inner class StartupWorkerListReceiver internal constructor(var myTag: String) : Consumer<Any?> {
        override fun accept(obj: Any?) {
            val workers = obj as StartupWorkerList?
            if (workers != null) {
                tr.eventi("loading startup worker list '$myTag'")
                workers.fetchFromODB(myODB, this@Workshop, tr)
            } else {
                tr.errori("unable to load startup worker list '" + myTag +
                        "'")
            }
        }

    }

    /**
     * Register a newly loaded workshop service with the broker.
     *
     * @param serviceName  The name of the service to register
     */
    fun registerService(serviceName: String?) {
        val services = myServer.services()
        val newServices: MutableList<ServiceDesc> = LinkedList()
        for (service in services) {
            if ("workshop-service" == service.service()) {
                newServices.add(service.subService(serviceName))
            }
        }
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
     *
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    fun shutdown(kill: Boolean) {
        myServer.shutdown(kill)
    }

    /**
     * Fetch an object from the repository.
     *
     * @param ref  The ref of the object desired.
     * @param handler  Callback that will be invoked with the object in
     * question, or null if the object was not available.
     */
    fun getObject(ref: String?, handler: Consumer<Any?>?) {
        myODB.getObject(ref, null, handler)
    }

    /**
     * Fetch an object from a particular collection in the repository.
     *
     * @param ref  The ref of the object desired.
     * @param collection  The name of the collection to use.
     * @param handler  Callback that will be invoked with the object in
     * question, or null if the object was not available.
     */
    fun getObject(ref: String?, collection: String?, handler: Consumer<Any?>?) {
        myODB.getObject(ref, collection, handler)
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
    fun queryObjects(query: JSONObject?, maxResults: Int,
                     handler: Consumer<Any?>?) {
        myODB.queryObjects(query, null, maxResults, handler)
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
    fun queryObjects(query: JSONObject?, collection: String?,
                     maxResults: Int, handler: Consumer<Any?>?) {
        myODB.queryObjects(query, collection, maxResults, handler)
    }

    /**
     * Store an object into the repository.
     *
     * @param ref  Ref of the object to write.
     * @param object  The object itself.
     */
    fun putObject(ref: String?, `object`: Encodable?) {
        myODB.putObject(ref, `object`, null, false, null)
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
    fun putObject(ref: String?, `object`: Encodable?, collection: String?,
                  resultHandler: Consumer<Any?>?) {
        myODB.putObject(ref, `object`, collection, false, resultHandler)
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
    fun updateObject(ref: String?, version: Int, `object`: Encodable?,
                     resultHandler: Consumer<Any?>?) {
        myODB.updateObject(ref, version, `object`, null, resultHandler)
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
    fun updateObject(ref: String?, version: Int, `object`: Encodable?,
                     collection: String?, resultHandler: Consumer<Any?>?) {
        myODB.updateObject(ref, version, `object`, collection, resultHandler)
    }

    /**
     * Delete an object from the repository.
     *
     * @param ref  The ref of the object to be deleted.
     */
    fun removeObject(ref: String?) {
        myODB.removeObject(ref, null, null)
    }

   init {
        if (odb == null) {
            tr.fatalError("no database specified")
        }
        myODB = odb
        myServer = server
        odb.addClass("auth", AuthDesc::class.java)
        addRef(ClientHandler(this, traceFactory))
        addRef(AdminHandler(this, traceFactory))
        isShuttingDown = false
        server.registerShutdownWatcher {
            isShuttingDown = true
            myODB.shutdown()
        }
    }
}