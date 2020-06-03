package org.elkoserver.foundation.server

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.json.AlwaysBaseTypeResolver
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Communication
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.ConnectionCountMonitor
import org.elkoserver.foundation.net.ConnectionRetrier
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.RunnerRef
import org.elkoserver.foundation.run.SlowServiceRunner
import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.foundation.server.metadata.ServiceFinder
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.objdb.ObjDB
import org.elkoserver.objdb.ObjDBLocal
import org.elkoserver.objdb.ObjDBRemoteFactory
import org.elkoserver.util.HashMapMulti
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag
import java.time.Clock
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedList
import java.util.StringTokenizer
import java.util.concurrent.Callable
import java.util.function.Consumer

/**
 * The core of an Elko server, holding the run queue, a collection of
 * configuration information extracted from the various Java property settings,
 * as well as access to various important server-intrinsic services (such as
 * port listeners) that are configured by those property settings.
 *
 * @param myProps  The properties, as determined by the boot process.
 * @param serverType  Server type tag (for generating property names).
 * @param tr  Trace object for event logging.
 * @param myTagGenerator Counter to generate tags for 'find' requests to the broker.
 */
class Server(
        private val myProps: ElkoProperties,
        serverType: String,
        private val gorgel: Gorgel,
        private val serviceLinkGorgel: Gorgel,
        private val serviceActorGorgel: Gorgel,
        private val baseConnectionSetupGorgel: Gorgel,
        private val objDbLocalGorgel: Gorgel,
        private val baseGorgel: Gorgel,
        private val connectionRetrierWithoutLabelGorgel: Gorgel,
        private val tr: Trace,
        private val timer: Timer,
        clock: Clock,
        private val traceFactory: TraceFactory,
        private val authDescFromPropertiesFactory: AuthDescFromPropertiesFactory,
        hostDescFromPropertiesFactory: HostDescFromPropertiesFactory,
        private val myTagGenerator: IdGenerator,
        private val myLoadMonitor: ServerLoadMonitor,
        sessionIdGenerator: IdGenerator,
        private val jsonToObjectDeserializer: JsonToObjectDeserializer,
        private val runnerRef: RunnerRef,
        private val objDBRemoteFactory: ObjDBRemoteFactory)
    : ConnectionCountMonitor, ServiceFinder {

    /** The name of this server (for logging).  */
    val serverName: String = myProps.getProperty("conf.$serverType.name", "<anonymous>")

    /** Name of service, to distinguish variants of same service type.  */
    private val myServiceName: String = myProps.getProperty("conf.$serverType.service")?.let { "-$it" } ?: ""

    /** List of ServiceDesc objects describing services this server offers.  */
    private val myServices: MutableList<ServiceDesc> = LinkedList()

    /** List of host information for this server's configured listeners.  */
    lateinit var listeners: List<HostDesc>

    /** Connection to the broker, if there is one.  */
    private var myBrokerActor: BrokerActor? = null

    /** Host description for connection to broker, if there is one.  */
    private val myBrokerHost: HostDesc? = hostDescFromPropertiesFactory.fromProperties("conf.broker")

    /** Message dispatcher for broker connections.  */
    private val myDispatcher = MessageDispatcher(AlwaysBaseTypeResolver, traceFactory, clock, jsonToObjectDeserializer)

    /** Table of 'find' requests that have been issued to the broker, for which
     * responses are still pending.  Indexed by the service name queried.  */
    private val myPendingFinds: HashMapMulti<String, ServiceQuery> = HashMapMulti()

    /** Number of active connections.  */
    private var myConnectionCount = 0

    /** Objects to be notified when the server is shutting down.  */
    private val myShutdownWatchers: MutableList<ShutdownWatcher> = LinkedList()

    /** Objects to be notified when the server is reinitialized.  */
    private val myReinitWatchers: MutableList<ReinitWatcher> = LinkedList()

    /** Run queue that the server services its clients in.  */
    private val myMainRunner = runnerRef.get()

    /** Network manager, for setting up network communications.  */
    val networkManager = NetworkManager(this, myProps, myLoadMonitor, myMainRunner, timer, clock, traceFactory, sessionIdGenerator)

    /** Thread pool isolation for external blocking tasks.  */
    private val mySlowRunner = SlowServiceRunner(myMainRunner, myProps.intProperty("conf.slowthreads", DEFAULT_SLOW_THREADS))

    /** Flag that server is in the midst of trying to shut down.  */
    private var amShuttingDown = false

    /** Map from external service names to links to the services.  */
    private val myServiceLinksByService: MutableMap<String, ServiceLink> = HashMap()

    /** Map from external service provider IDs to connected actors.  */
    private val myServiceActorsByProviderID: MutableMap<Int, ServiceActor> = HashMap()

    /** Active service actors associated with broken broker connections.  */
    private val myOldServiceActors: MutableList<ServiceActor> = LinkedList()

    /* RefTable to dispatching messages incoming from external services. */
    private var myServiceRefTable: RefTable? = null

    /**
     * Take note of the connection to the broker.  Send any 'find' requests
     * that were issued prior to the broker connection being made.
     *
     * @param brokerActor  Actor representing the connection to the broker.
     * If null, this indicates that a connection has been lost and must be
     * re-established.
     */
    fun brokerConnected(brokerActor: BrokerActor?) {
        myBrokerActor = brokerActor
        if (brokerActor == null) {
            myOldServiceActors.addAll(myServiceActorsByProviderID.values)
            myServiceActorsByProviderID.clear()
            myServiceLinksByService.clear()
            connectToBroker()
        } else {
            for (key in myPendingFinds.keys()) {
                for (query in myPendingFinds.getMulti(key)) {
                    brokerActor.findService(key, query.isMonitor, query.tag)
                }
            }
        }
    }

    /**
     * Make a new connection to the broker.
     */
    private fun connectToBroker() {
        if (!amShuttingDown) {
            ConnectionRetrier(myBrokerHost!!, "broker", networkManager, BrokerMessageHandlerFactory(), timer, connectionRetrierWithoutLabelGorgel.withAdditionalStaticTags(Tag("label", "broker")), tr, traceFactory)
        }
    }

    private inner class BrokerMessageHandlerFactory : MessageHandlerFactory {
        override fun provideMessageHandler(connection: Connection?): MessageHandler = BrokerActor(connection, myDispatcher, this@Server, myBrokerHost!!, traceFactory)
    }

    /**
     * Track the number of connections, so server can exit gracefully.
     *
     * @param delta  An upward or downward adjustment to the connection count.
     */
    @Synchronized
    override fun connectionCountChange(delta: Int) {
        myConnectionCount += delta
        if (myConnectionCount < 0) {
            gorgel.error("negative connection count: $myConnectionCount")
        }
        if (amShuttingDown && myConnectionCount <= 0) {
            serverExit()
        }
    }

    /**
     * Drop a runnable onto the main run queue.
     *
     * @param runnable  The thing to run.
     */
    fun enqueue(runnable: Runnable?) {
        myMainRunner.enqueue(runnable)
    }

    /**
     * Drop a task onto the slow queue.
     *
     * @param task  Callable that executes the task.  This will be executed in
     * a separate thread and so is permitted to block.
     * @param resultHandler  Thunk that will be invoked with the result
     * returned by the task.  This will be executed on the main run queue.
     */
    fun enqueueSlowTask(task: Callable<Any?>?,
                        resultHandler: Consumer<Any?>?) {
        mySlowRunner.enqueueTask(task!!, resultHandler)
    }

    /**
     * Attempt to reestablish a broken service connection.
     *
     * @param service  Name of the service to reconnect to
     * @param link  Service link to associate with the reestablished connection
     */
    fun reestablishServiceConnection(service: String, link: ServiceLink) {
        findService(service,
                ServiceFoundHandler(Consumer { obj: ServiceLink? ->
                    if (obj == null) {
                        link.fail()
                    }
                }, service, link), false)
    }

    /**
     * Issue a request for service information to the broker.
     *
     * @param service  The service desired.
     * @param handler  Object to receive the asynchronous result(s).
     * @param monitor  If true, keep watching for more results after the first.
     */
    override fun findService(service: String, handler: Consumer<in Array<ServiceDesc>>, monitor: Boolean) {
        if (myBrokerHost != null) {
            val tag = myTagGenerator.generate().toString()
            myPendingFinds.add(service, ServiceQuery(service, handler, monitor, tag))
            myBrokerActor?.run {
                findService(service, monitor, tag)
            }
        } else {
            gorgel.error("can't find service $service, no broker specified")
        }
    }

    /**
     * Obtain a message channel to a service.  Services are located using the
     * broker.  Note that all services offered by a given server are
     * multiplexed through a single connection: if there is an existing
     * connection to the server providing the service, it is used, but if there
     * is no existing connection to that server, one is created.
     *
     * @param service  The service desired
     * @param handler A runnable that will be invoked with a service link to
     * the requested service once the connection is located or created.  The
     * handler will be passed a null if no connection was possible.
     */
    fun findServiceLink(service: String, handler: Consumer<in ServiceLink?>) {
        if (!amShuttingDown) {
            val link = myServiceLinksByService[service]
            if (link != null) {
                handler.accept(link)
            } else {
                findService(service, ServiceFoundHandler(handler, service, null), false)
            }
        }
    }

    /**
     * Handler class to process events associated with establishing a service
     * connection.  Handles (1) processing the result returned by the broke
     * when asked for contact information for a service, and (2) processing the
     * connection to the relevant server once such a connection is first
     * established.
     *
     * @param myInnerHandler  Handler to receive service connection.
     * @param myLabel  Name of the service being sought.
     * @param link  Service link that will be associated with the
     * connection; if null, a new link will be created
     */
    private inner class ServiceFoundHandler internal constructor(
            private val myInnerHandler: Consumer<in ServiceLink?>,
            private val myLabel: String, link: ServiceLink?) : Consumer<Array<ServiceDesc>>, MessageHandlerFactory {

        /** Service descriptor from the broker, once we have one.  */
        private var myDesc: ServiceDesc? = null

        /** A service link to be associated with the connection, or null if a
         * new link should be allocated.  */
        private val myLink: ServiceLink = link ?: ServiceLink(myLabel, this@Server, serviceLinkGorgel)

        /**
         * Handle the location of a service by the broker.  If we already have
         * an existing connection to the server providing the service, the
         * service actor associated with that connection is passed to the
         * handler from the original requestor.  Otherwise, we initiate a new
         * connection to the server in question.
         *
         * Note that the broker can return multiple providers for a service,
         * but for the purposes of this API we want exactly one. If more than
         * one provider was located we arbitraily choose the first one but also
         * write a warning to the log.
         *
         * @param obj  Array of service descriptors for the service that was
         * located.  Normally this will be a single element array.
         */
        override fun accept(obj: Array<ServiceDesc>) {
            val desc = obj[0]
            myDesc = desc
            if (desc.failure != null) {
                gorgel.warn("service query for $myLabel failed: ${desc.failure}")
                myInnerHandler.accept(null)
                return
            }
            if (obj.size > 1) {
                gorgel.warn("service query for $myLabel returned multiple results; using first one")
            }
            val actor = myServiceActorsByProviderID[desc.providerID]
            actor?.let(::connectLinkToActor)
                    ?: ConnectionRetrier(desc.asHostDesc(-1), myLabel,
                            networkManager, this, timer, connectionRetrierWithoutLabelGorgel.withAdditionalStaticTags(Tag("label", myLabel)), tr, traceFactory)
        }

        /**
         * Provide a message handler for a new external server connection.
         *
         * @param connection  The Connection object that was just created.
         */
        override fun provideMessageHandler(connection: Connection?): MessageHandler {
            val actor = ServiceActor(connection!!, myServiceRefTable!!, myDesc!!,
                    this@Server, serviceActorGorgel, traceFactory)
            myServiceActorsByProviderID[myDesc!!.providerID] = actor
            connectLinkToActor(actor)
            return actor
        }

        /**
         * Common code for wiring a ServiceLink to a ServiceActor and notifying
         * the service requestor, regardless of whether the actor was found or
         * created.
         *
         * @param actor The actor for the new service link.
         */
        private fun connectLinkToActor(actor: ServiceActor) {
            myServiceLinksByService[myDesc!!.service] = myLink
            actor.addLink(myLink)
            myInnerHandler.accept(myLink)
        }

    }

    /**
     * Handle the broker announcing a service.
     *
     * @param services  Descriptions of the services found.
     * @param tag  Tag string for matching queries with responses.
     */
    fun foundService(services: Array<ServiceDesc>, tag: String) {
        val service = services[0].service /* all must have same name */
        val iter = myPendingFinds.getMulti(service).iterator()
        while (iter.hasNext()) {
            val query = iter.next()
            if (tag == query.tag) {
                query.result(services)
                if (!query.isMonitor) {
                    iter.remove()
                }
            }
        }
    }

    /**
     * Open an asynchronous object database whose location (directory path or
     * remote repository host) is specified by properties.
     *
     * @param propRoot  Prefix string for all the properties describing the odb
     * that is to be opened.
     *
     * @return an object for communicating with the opened odb, or null if the
     * location was not properly specified.
     */
    fun openObjectDatabase(propRoot: String): ObjDB? {
        return if (myProps.getProperty("$propRoot.odb") != null) {
            ObjDBLocal(myProps, propRoot, objDbLocalGorgel, baseGorgel, traceFactory, jsonToObjectDeserializer, runnerRef)
        } else {
            if (myProps.getProperty("$propRoot.repository.host") != null ||
                    myProps.getProperty("$propRoot.repository.service") != null) {
                objDBRemoteFactory.create(this, networkManager, serverName, propRoot)
            } else {
                null
            }
        }
    }

    /**
     * Add an object to the collection of objects to be notified when the
     * server samples its load.
     *
     * @param watcher  An object to notify about load samples.
     */
    fun registerLoadWatcher(watcher: LoadWatcher) {
        myLoadMonitor.registerLoadWatcher(watcher)
    }

    /**
     * Remove an object from the collection of objects that are notified when
     * the server samples its load.
     *
     * @param watcher  The object to stop notifying about load samples.
     */
    fun unregisterLoadWatcher(watcher: LoadWatcher) {
        myLoadMonitor.unregisterLoadWatcher(watcher)
    }

    /**
     * Add an object to the collection of objects to be notified when the
     * server is being reinitialized.
     *
     * @param watcher  An object to notify at reinitialization time.
     */
    fun registerReinitWatcher(watcher: ReinitWatcher) {
        myReinitWatchers.add(watcher)
    }

    /**
     * Remove an object from the collection of objects that are notified when
     * the server is being reinitialized.
     *
     * @param watcher  The object that no longer cares about reinitialization.
     */
    fun unregisterReinitWatcher(watcher: ReinitWatcher) {
        myReinitWatchers.remove(watcher)
    }

    /**
     * Add a new service offering to the collection of services provided by
     * this server.
     *
     * @param service  Service descriptor describing the service being added.
     */
    fun registerService(service: ServiceDesc) {
        myServices.add(service)
        if (myBrokerActor != null) {
            myBrokerActor!!.registerService(service)
        }
    }

    /**
     * Add an object to the collection of objects to be notified when the
     * server is being shut down.
     *
     * @param watcher  An object to notify at shutdown time.
     */
    fun registerShutdownWatcher(watcher: ShutdownWatcher) {
        myShutdownWatchers.add(watcher)
    }

    /**
     * Remove an object from the collection of objects that are notified when
     * the server is being shut down.
     *
     * @param watcher  The object that no longer cares about shutdown.
     */
    fun unregisterShutdownWatcher(watcher: ShutdownWatcher) {
        myShutdownWatchers.remove(watcher)
    }

    /**
     * Reinitialize the server.
     */
    fun reinit() {
        if (myBrokerActor != null) {
            myBrokerActor!!.close()
        }
        for (watcher in myReinitWatchers) {
            watcher.noteReinit()
        }
    }

    /**
     * Really shut down the server.
     */
    private fun serverExit() {
        gorgel.i?.run { info("Good bye") }
        myMainRunner.orderlyShutdown()
    }

    fun serviceActorDied(deadActor: ServiceActor) {
        myServiceActorsByProviderID.remove(deadActor.providerID)
        for (link in deadActor.serviceLinks) {
            myServiceLinksByService.remove(link.service)
        }
    }

    /**
     * Get the services being offered by this server.
     *
     * @return a list of ServiceDesc objects describing the services offered by
     * this server.
     */
    fun services(): List<ServiceDesc> = myServices

    /**
     * Assign the ref table that will be used to dispatch messages received
     * from connected services.
     *
     * @param serviceRefTable  The ref table to use.
     */
    fun setServiceRefTable(serviceRefTable: RefTable?) {
        myServiceRefTable = serviceRefTable
    }

    /**
     * Shut down the server.  Actually, this initiates the shutdown process;
     * the actual shutdown happens in serverExit().
     */
    fun shutdown() {
        if (!amShuttingDown) {
            amShuttingDown = true
            gorgel.i?.run { info("Shutting down $serverName") }
            if (myBrokerActor != null) {
                myBrokerActor!!.close()
            }
            for (watcher in myShutdownWatchers) {
                watcher.noteShutdown()
            }
            for (service in myServiceActorsByProviderID.values) {
                service.close()
            }
            for (service in myOldServiceActors) {
                service.close()
            }
            if (myConnectionCount <= 0) {
                serverExit()
            }
        }
    }

    /**
     * Start listening for connections on some port.
     *
     * @param propRoot  Prefix string for all the properties describing the
     * listener that is to be started.
     * @param host  The host:port string for the port to listen on.
     * @param metaFactory   Object that will provide a message handler factory
     * for connections made to this listener.
     *
     * @return host description for the listener that was started, or null if
     * the operation failed for some reason.
     */
    private fun startOneListener(propRoot: String, host: String,
                                 metaFactory: ServiceFactory): HostDesc? {
        val auth = authDescFromPropertiesFactory.fromProperties(propRoot)
        val allowString = myProps.getProperty("$propRoot.allow")
        val allow: MutableSet<String> = HashSet()
        if (allowString != null) {
            val scan = StringTokenizer(allowString, ",")
            while (scan.hasMoreTokens()) {
                allow.add(scan.nextToken())
            }
        }
        val protocol = myProps.getProperty("$propRoot.protocol", "tcp")
        val serviceNames: MutableList<String> = LinkedList()
        val actorFactory = metaFactory.provideFactory(propRoot, auth, allow, serviceNames, protocol)
        val label = myProps.getProperty("$propRoot.label")
        val secure = myProps.testProperty("$propRoot.secure")
        val mgrClass = myProps.getProperty("$propRoot.class")
        val connectionSetup: ConnectionSetup
        connectionSetup = mgrClass?.let { ManagerClassConnectionSetup(label, it, host, auth, secure, myProps, propRoot, networkManager, actorFactory, baseConnectionSetupGorgel, traceFactory) }
                ?: when (protocol) {
                    "tcp" -> TcpConnectionSetup(label, host, auth, secure, myProps, propRoot, networkManager, actorFactory, baseConnectionSetupGorgel, traceFactory)
                    "rtcp" -> RtcpConnectionSetup(label, host, auth, secure, myProps, propRoot, networkManager, actorFactory, baseConnectionSetupGorgel, traceFactory)
                    "http" -> HttpConnectionSetup(label, host, auth, secure, myProps, propRoot, networkManager, actorFactory, baseConnectionSetupGorgel, traceFactory)
                    "ws" -> WebSocketConnectionSetup(label, host, auth, secure, myProps, propRoot, networkManager, actorFactory, baseConnectionSetupGorgel, traceFactory)
                    else -> {
                        gorgel.error("unknown value for $propRoot.protocol: $protocol, listener $propRoot not started")
                        throw IllegalStateException()
                    }
                }
        connectionSetup.startListener()
        serviceNames
                .map { "$it$myServiceName" }
                .forEach { registerService(ServiceDesc(it, host, protocol, label, auth, null, -1)) }
        return HostDesc(protocol, secure, connectionSetup.serverAddress, auth, -1)
    }

    /**
     * Start listening for connections on all the ports that are configured.
     *
     * @param propRoot  Prefix string for all the properties describing the
     * listeners that are to be started.
     * @param serviceFactory   Object to provide message handler factories for
     * the new listeners.
     *
     * @return the number of ports that were configured.
     */
    fun startListeners(propRoot: String, serviceFactory: ServiceFactory): Int {
        var listenerPropRoot = propRoot
        var listenerCount = 0
        val theListeners: MutableList<HostDesc> = LinkedList()
        while (true) {
            val hostName = myProps.getProperty("$listenerPropRoot.host") ?: break
            val listener = startOneListener(listenerPropRoot, hostName, serviceFactory)
            if (listener != null) {
                theListeners.add(listener)
            }
            listenerPropRoot = propRoot + ++listenerCount
        }
        if (myBrokerHost != null) {
            connectToBroker()
        }
        listeners = theListeners
        return listenerCount
    }

    companion object {
        /** Default value for max number of threads in slow service thread pool.  */
        private const val DEFAULT_SLOW_THREADS = 5
    }

    init {
        gorgel.i?.run {
            info(BuildVersion.version)
            info(("Copyright 2016 ElkoServer.org; see LICENSE"))
            info("Starting $serverName")
        }

        myDispatcher.addClass(BrokerActor::class.java)

        if (myProps.testProperty("conf.msgdiagnostics")) {
            Communication.TheDebugReplyFlag = true
        }
    }
}
