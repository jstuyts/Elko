package org.elkoserver.foundation.server

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.json.AlwaysBaseTypeResolver
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.ConnectionSetupFactory
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.foundation.run.SlowServiceRunner
import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.foundation.server.metadata.ServiceFinder
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.objdb.ObjDB
import org.elkoserver.objdb.ObjDBLocalFactory
import org.elkoserver.objdb.ObjDBRemoteFactory
import org.elkoserver.util.HashMapMulti
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.slf4j.Gorgel
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
        private val serviceActorCommGorgel: Gorgel,
        private val brokerActorGorgel: Gorgel,
        private val tr: Trace,
        methodInvokerCommGorgel: Gorgel,
        private val authDescFromPropertiesFactory: AuthDescFromPropertiesFactory,
        hostDescFromPropertiesFactory: HostDescFromPropertiesFactory,
        private val myTagGenerator: IdGenerator,
        private val myLoadMonitor: ServerLoadMonitor,
        jsonToObjectDeserializer: JsonToObjectDeserializer,
        private val runner: Runner,
        private val objDBRemoteFactory: ObjDBRemoteFactory,
        private val mustSendDebugReplies: Boolean,
        private val objDBLocalFactory: ObjDBLocalFactory,
        private val connectionSetupFactoriesByCode: Map<String, ConnectionSetupFactory>,
        private val connectionRetrierFactory: ConnectionRetrierFactory)
    : ServiceFinder {

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
    private val myDispatcher = MessageDispatcher(AlwaysBaseTypeResolver, methodInvokerCommGorgel, jsonToObjectDeserializer)

    /** Table of 'find' requests that have been issued to the broker, for which
     * responses are still pending.  Indexed by the service name queried.  */
    private val myPendingFinds: HashMapMulti<String, ServiceQuery> = HashMapMulti()

    /** Objects to be notified when the server is shutting down.  */
    private val myShutdownWatchers: MutableList<ShutdownWatcher> = LinkedList()

    /** Objects to be notified when the server is reinitialized.  */
    private val myReinitWatchers: MutableList<ReinitWatcher> = LinkedList()

    /** Thread pool isolation for external blocking tasks.  */
    private val mySlowRunner = SlowServiceRunner(runner, myProps.intProperty("conf.slowthreads", DEFAULT_SLOW_THREADS))

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
            connectionRetrierFactory.create(myBrokerHost!!, "broker", BrokerMessageHandlerFactory())
        }
    }

    private inner class BrokerMessageHandlerFactory : MessageHandlerFactory {
        override fun provideMessageHandler(connection: Connection?): MessageHandler = BrokerActor(connection!!, myDispatcher, this@Server, myBrokerHost!!, brokerActorGorgel, mustSendDebugReplies)
    }

    /**
     * Drop a runnable onto the main run queue.
     *
     * @param runnable  The thing to run.
     */
    fun enqueue(runnable: Runnable?) {
        runner.enqueue(runnable)
    }

    /**
     * Drop a task onto the slow queue.
     *
     * @param task  Callable that executes the task.  This will be executed in
     * a separate thread and so is permitted to block.
     * @param resultHandler  Thunk that will be invoked with the result
     * returned by the task.  This will be executed on the main run queue.
     */
    fun enqueueSlowTask(task: Callable<Any?>, resultHandler: Consumer<Any?>?) {
        mySlowRunner.enqueueTask(task, resultHandler)
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
                    ?: connectionRetrierFactory.create(desc.asHostDesc(-1), myLabel, this)
        }

        /**
         * Provide a message handler for a new external server connection.
         *
         * @param connection  The Connection object that was just created.
         */
        override fun provideMessageHandler(connection: Connection?): MessageHandler {
            val actor = ServiceActor(connection!!, myServiceRefTable!!, myDesc!!,
                    this@Server, serviceActorGorgel, serviceActorCommGorgel, mustSendDebugReplies)
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
            objDBLocalFactory.create(propRoot)
        } else {
            if (myProps.getProperty("$propRoot.repository.host") != null ||
                    myProps.getProperty("$propRoot.repository.service") != null) {
                objDBRemoteFactory.create(this, serverName, propRoot)
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
        myBrokerActor?.registerService(service)
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
        myBrokerActor?.close()
        for (watcher in myReinitWatchers) {
            watcher.noteReinit()
        }
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
            myBrokerActor?.close()
            for (watcher in myShutdownWatchers) {
                watcher.noteShutdown()
            }
            for (service in myServiceActorsByProviderID.values) {
                service.close()
            }
            for (service in myOldServiceActors) {
                service.close()
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
                                 metaFactory: ServiceFactory): HostDesc {
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
        val connectionSetup = connectionSetupFactoriesByCode[protocol]?.create(label, host, auth, secure, propRoot, actorFactory)
        if (connectionSetup == null) {
            gorgel.error("unknown value for $propRoot.protocol: $protocol, listener $propRoot not started")
            throw IllegalStateException()
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
            theListeners.add(listener)
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
    }
}
