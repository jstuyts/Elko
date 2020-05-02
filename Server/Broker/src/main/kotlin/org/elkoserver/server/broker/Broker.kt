package org.elkoserver.server.broker

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.json.AlwaysBaseTypeResolver
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.metadata.LoadDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.objdb.ObjDB
import org.elkoserver.server.broker.LauncherTable.Companion.setTrace
import org.elkoserver.util.HashMapMulti
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.time.Clock
import java.util.LinkedList
import java.util.function.Consumer

/**
 * Main state data structure in a Broker.
 *
 * @param myServer  Server object.
 * @param tr  Trace object for diagnostics.
 */
internal class Broker(private val myServer: Server, private val tr: Trace, private val timer: Timer, traceFactory: TraceFactory?, clock: Clock?) {
    /** Table for mapping object references in messages.  */
    private val myRefTable = RefTable(AlwaysBaseTypeResolver, traceFactory, clock)

    /** Database for configuration data.  */
    private val myODB: ObjDB?

    /** Registered services.  Maps (service name, protocol) pairs to sets of
     * ServiceDesc objects.  */
    private val myServices = HashMapMulti<String, ServiceDesc>()

    /** Clients waiting for services.  Maps service names to sets of
     * WaiterForService objects.  */
    private val myWaiters = HashMapMulti<String, WaiterForService>()

    /** Set of currently connected actors.  */
    private val myActors: MutableSet<BrokerActor> = HashSet()

    /** Set of clients watching servers come & go.  */
    private val myServiceWatchers: MutableSet<BrokerActor> = HashSet()

    /** Set of clients watching load status.  */
    private val myLoadWatchers: MutableSet<BrokerActor> = HashSet()

    /** The admin object.  */
    private val myAdminHandler: AdminHandler

    /** The client object.  */
    private val myClientHandler: ClientHandler = ClientHandler(this, traceFactory)

    /** Table of servers that this broker can launch.  */
    private var myLauncherTable: LauncherTable?

    /**
     * Get a read-only view of the set of connected actors.
     *
     * @return the set of connected actors.
     */
    fun actors(): Set<BrokerActor> = myActors

    /**
     * Add a new actor to the table of connected actors.
     *
     * @param actor  The actor to add.
     */
    fun addActor(actor: BrokerActor) {
        myActors.add(actor)
    }

    /**
     * Add a new service to the table of registered services.
     *
     * @param service  Description of the service to add.
     */
    fun addService(service: ServiceDesc) {
        myServices.add(serviceKey(service.service(), service.protocol()!!), service)
        noteServiceArrival(service)
    }

    /**
     * Make sure the state of the launch table is saved in persistent form.
     */
    fun checkpoint() {
        myLauncherTable?.checkpoint(myODB)
    }

    /**
     * Get the handler for client messages.
     */
    fun clientHandler() = myClientHandler

    /**
     * Obtain this broker's launcher table.
     *
     * @return the launch table.
     */
    fun launcherTable() = myLauncherTable

    /**
     * Return an iterable over a set of registered services.
     *
     * @param service  The name of the service sought, or null to get them all.
     * @param protocol  The name of the protocol sought
     *
     * @return an iterable that can be iterated over the registered services
     * with the given name.
     */
    fun services(service: String?, protocol: String) =
            if (service == null) {
                myServices.values()
            } else {
                myServices.getMulti(serviceKey(service, protocol))
            }

    /**
     * Take note that a new service is available, in case anybody is waiting.
     *
     * @param service  Description of the new service.
     */
    private fun noteServiceArrival(service: ServiceDesc) {
        val deadWaiters: MutableList<WaiterForService> = LinkedList()
        for (waiter in myWaiters.getMulti(service.service())) {
            if (waiter.noteServiceArrival(service)) {
                deadWaiters.add(waiter)
            }
        }
        for (waiter in deadWaiters) {
            myWaiters.remove(waiter.service(), waiter)
        }
        val msg = AdminHandler.msgServiceDesc(myAdminHandler, service.encodeAsArray(), true)
        for (watcher in myServiceWatchers) {
            watcher.send(msg)
        }
    }

    /**
     * Take note that new load information is available, in case anybody is
     * waiting.
     *
     * @param server  Server for which new load information is available.
     */
    fun noteLoadDesc(server: BrokerActor) {
        val client = server.client()
        val desc = LoadDesc(server.label()!!, client!!.loadFactor(), client.providerID())
        val msg = AdminHandler.msgLoadDesc(myAdminHandler, desc.encodeAsArray())
        for (watcher in myLoadWatchers) {
            watcher.send(msg)
        }
    }

    /**
     * Take note that a service is has disappeared, in case anybody is
     * watching.
     *
     * @param service  Description of the service that went away.
     */
    private fun noteServiceDeparture(service: ServiceDesc) {
        val msg = AdminHandler.msgServiceDesc(myAdminHandler, service.encodeAsArray(), false)
        for (watcher in myServiceWatchers) {
            watcher.send(msg)
        }
    }

    /**
     * Return the object ref table.
     */
    fun refTable() = myRefTable

    /**
     * Reinitialize the server.
     */
    fun reinitServer() {
        myServer.reinit()
    }

    /**
     * Remove an actor from the set of connected actors.
     *
     * @param actor  The actor to remove.
     */
    fun removeActor(actor: BrokerActor) {
        myActors.remove(actor)
    }

    /**
     * Remove a service to the table of registered services.
     *
     * @param service  Description of the service to remove.
     */
    fun removeService(service: ServiceDesc) {
        myServices.remove(serviceKey(service.service(), service.protocol()!!), service)
        noteServiceDeparture(service)
    }

    /**
     * Turn a service name and protocol name into a key into the services
     * table.
     *
     * @param service  The service name
     * @param protocol  The protocol name
     *
     * @return a string for indexing the service + protocol pair.
     */
    private fun serviceKey(service: String, protocol: String) = "$service+$protocol"

    /**
     * Shutdown the server.
     *
     * @param kill  If true, shutdown immediately without cleaning up.
     */
    fun shutdownServer(kill: Boolean) {
        if (!kill) {
            for (actor in LinkedList(myActors)) {
                actor.doDisconnect()
            }
        }
        myODB?.shutdown()
        myServer.shutdown(kill)
    }

    /**
     * Remove somebody from the collection of clients watching system load.
     *
     * @param who  The new ex-watcher.
     */
    fun unwatchLoad(who: BrokerActor) {
        myLoadWatchers.remove(who)
    }

    /**
     * Remove somebody from the collection of clients watching services come &
     * go.
     *
     * @param who  The new ex-watcher.
     */
    fun unwatchServices(who: BrokerActor) {
        myServiceWatchers.remove(who)
    }

    /**
     * Take note that somebody is waiting for a service to appear.
     *
     * @param service  The name of the service sought.
     * @param who  The client who is waiting.
     * @param keepWatching  Flag to keep waiting, even if the service appears.
     * @param timeout  How long to wait before giving up, in seconds (negative
     * to wait forever).
     * @param failOK  Flag that is true if failure is an option.
     * @param tag  Arbitrary tag that will be sent back with the response, to
     * match up requests and responses.
     */
    fun waitForService(service: String, who: BrokerActor, keepWatching: Boolean,
                       timeout: Int, failOK: Boolean, tag: String?) {
        val waiter = WaiterForService(service, who, keepWatching, timeout, failOK, tag, timer)
        myWaiters.add(service, waiter)
    }

    /**
     * Object representing one client waiting for one service.
     *
     * @param myService  The name of the service sought.
     * @param myWaiter  The client who is waiting.
     * @param amKeepWatching  Flag to keep waiting, even if the service
     *    appears.
     * @param timeout  How long to wait before giving up, in seconds
     *    (negative to wait forever).
     * @param failOK  Flag that is true if failure *is* an option.
     * @param myTag  Arbitrary tag that will be sent back with the response,
     *    to match up requests and responses.
     */
    private inner class WaiterForService internal constructor(private val myService: String, private val myWaiter: BrokerActor,
                                                              private val amKeepWatching: Boolean, timeout: Int, private var amSuccessful: Boolean, private val myTag: String?, timer: Timer) : TimeoutNoticer {
        private var myTimeout: Timeout? = null

        /**
         * Take note of a service having arrived.  Notify the actor who was
         * waiting and record the fact that this was successful.
         *
         * @param service  The service that arrived.
         *
         * @return true if the the caller should stop waiting after this.
         */
        fun noteServiceArrival(service: ServiceDesc): Boolean {
            amSuccessful = true
            myTimeout?.let {
                if (!amKeepWatching) {
                    it.cancel()
                    myTimeout = null
                }
            }
            myClientHandler.findSuccess(myWaiter, service, myTag)
            return !amKeepWatching
        }

        /**
         * Handle the expiration of the impatience timer.
         */
        override fun noticeTimeout() {
            myTimeout = null
            myWaiters.remove(myService, this)
            if (!amSuccessful) {
                myClientHandler.findFailure(myWaiter, myService, myTag)
            }
        }

        /**
         * Return the service name being waited for.
         */
        fun service() = myService

        init {
            myTimeout = if (timeout > 0) timer.after(timeout * 1000.toLong(), this) else null
        }
    }

    /**
     * Add somebody to the collection of clients watching system load.
     *
     * @param who  The new watcher.
     */
    fun watchLoad(who: BrokerActor) {
        myLoadWatchers.add(who)
    }

    /**
     * Add somebody to the collection of clients watching services come & go.
     *
     * @param who  The new watcher.
     */
    fun watchServices(who: BrokerActor) {
        myServiceWatchers.add(who)
    }

    init {
        myRefTable.addRef(myClientHandler)
        myAdminHandler = AdminHandler(this, traceFactory)
        myRefTable.addRef(myAdminHandler)
        val startModeStr = myServer.props().getProperty("conf.broker.startmode")
        val startMode: Int
        startMode = when (startModeStr) {
            null, "initial" -> LauncherTable.START_INITIAL
            "recover" -> LauncherTable.START_RECOVER
            "restart" -> LauncherTable.START_RESTART
            else -> {
                tr.errorm("unknown startmode value '$startModeStr'")
                LauncherTable.START_RECOVER
            }
        }
        setTrace(tr)
        myLauncherTable = null
        myODB = myServer.openObjectDatabase("conf.broker")
        if (myODB != null) {
            myODB.addClass("launchertable", LauncherTable::class.java)
            myODB.addClass("launcher", LauncherTable.Launcher::class.java)
            myODB.getObject("launchertable", null, Consumer { obj: Any? ->
                if (obj != null) {
                    myLauncherTable = (obj as LauncherTable?)?.apply {
                        doStartupLaunches(startMode)
                    }
                } else {
                    tr.warningm("unable to load launcher table")
                    myLauncherTable = LauncherTable("launchertable", arrayOf())
                }
            })
        } else {
            tr.warningm("no database specified for launcher configuration")
        }
    }
}
