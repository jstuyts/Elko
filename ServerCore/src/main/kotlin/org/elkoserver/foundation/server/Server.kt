package org.elkoserver.foundation.server

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.ConnectionSetupFactory
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.foundation.server.metadata.ServiceFinder
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.HashMapMulti
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.HashMap
import java.util.LinkedList
import java.util.function.Consumer

/**
 * The core of an Elko server, holding the run queue, a collection of
 * configuration information extracted from the various Java property settings,
 * as well as access to various important server-intrinsic services (such as
 * port listeners) that are configured by those property settings.
 *
 * @param myProps  The properties, as determined by the boot process.
 * @param myTagGenerator Counter to generate tags for 'find' requests to the broker.
 */
class Server(
        private val myProps: ElkoProperties,
        private val description: ServerDescription,
        private val gorgel: Gorgel,
        private val serviceLinkGorgel: Gorgel,
        private val brokerActorFactory: BrokerActorFactory,
        private val serviceActorFactory: ServiceActorFactory,
        myDispatcher: MessageDispatcher,
        private val listenerConfigurationFromPropertiesFactory: ListenerConfigurationFromPropertiesFactory,
        private val myBrokerHost: HostDesc?,
        private val myTagGenerator: IdGenerator,
        private val connectionSetupFactoriesByCode: Map<String, ConnectionSetupFactory>,
        private val connectionRetrierFactory: ConnectionRetrierFactory)
    : ServiceFinder {

    /** The name of this server (for logging).  */
    val serverName: String = description.serverName

    /** List of ServiceDesc objects describing services this server offers.  */
    private val myServices: MutableList<ServiceDesc> = LinkedList()

    /** List of host information for this server's configured listeners.  */
    lateinit var listeners: List<HostDesc>

    /** Connection to the broker, if there is one.  */
    private var myBrokerActor: BrokerActor? = null

    /** Table of 'find' requests that have been issued to the broker, for which
     * responses are still pending.  Indexed by the service name queried.  */
    private val myPendingFinds: HashMapMulti<String, ServiceQuery> = HashMapMulti()

    /** Objects to be notified when the server is shutting down.  */
    private val myShutdownWatchers: MutableList<ShutdownWatcher> = LinkedList()

    /** Objects to be notified when the server is reinitialized.  */
    private val myReinitWatchers: MutableList<ReinitWatcher> = LinkedList()

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
        override fun provideMessageHandler(connection: Connection?): MessageHandler = brokerActorFactory.create(connection!!, this@Server, myBrokerHost!!)
    }

    /**
     * Attempt to reestablish a broken service connection.
     *
     * @param service  Name of the service to reconnect to
     * @param link  Service link to associate with the reestablished connection
     */
    fun reestablishServiceConnection(service: String, link: ServiceLink) {
        findService(service,
                ServiceFoundHandler({ obj: ServiceLink? ->
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
    private inner class ServiceFoundHandler(
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
         * one provider was located we arbitrarily choose the first one but also
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
            val actor = serviceActorFactory.create(connection!!, myServiceRefTable!!, myDesc!!, this@Server)
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
            gorgel.i?.run { info("Shutting down ${description.serverName}") }
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
    private fun startOneListener(propRoot: String, host: String, metaFactory: ServiceFactory) =
            startOneListener(propRoot, host, metaFactory, listenerConfigurationFromPropertiesFactory.read(propRoot))

    private fun startOneListener(propRoot: String, host: String, metaFactory: ServiceFactory, configuration: ListenerConfiguration) =
            configuration.run {
                val serviceNames: MutableList<String> = LinkedList()
                val actorFactory = metaFactory.provideFactory(propRoot, auth, allow, serviceNames, protocol)
                val connectionSetup = connectionSetupFactoriesByCode[protocol]?.create(label, host, auth, secure, propRoot, actorFactory)
                if (connectionSetup == null) {
                    gorgel.error("unknown value for $propRoot.protocol: $protocol, listener $propRoot not started")
                    throw IllegalStateException()
                }
                connectionSetup.startListener()
                serviceNames
                        .map { "$it${description.serviceName}" }
                        .forEach { registerService(ServiceDesc(it, host, protocol, label, auth, null, -1)) }
                HostDesc(protocol, secure, connectionSetup.serverAddress, auth, -1)
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
        var hostName = myProps.getProperty("$listenerPropRoot.host")
        while (hostName != null) {
            val listener = startOneListener(listenerPropRoot, hostName, serviceFactory)
            theListeners.add(listener)

            listenerPropRoot = propRoot + ++listenerCount

            hostName = myProps.getProperty("$listenerPropRoot.host")
        }
        if (myBrokerHost != null) {
            connectToBroker()
        }
        listeners = theListeners
        return listenerCount
    }

    init {
        gorgel.i?.run {
            info(BuildVersion.version)
            info(("Copyright 2016 ElkoServer.org; see LICENSE"))
            info("Starting ${description.serverName}")
        }

        myDispatcher.addClass(BrokerActor::class.java)
    }
}
