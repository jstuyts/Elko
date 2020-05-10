package org.elkoserver.server.broker

import org.elkoserver.foundation.boot.Bootable
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.ConstantDefinition
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphLogger
import org.ooverkommelig.ProvidedAdministration
import java.time.Clock

/**
 * The Elko boot class for the Broker.  The Broker is a server allows a
 * cluster of Elko servers of various kinds to find out information about each
 * other's available services (and thus establish interconnectivity) without
 * having to be preconfigured.  It also shields the various servers from
 * order-of-startup issues.  Finally, it provides a place to stand for
 * monitoring and administering a Elko server farm as a whole.
 */
@Suppress("unused")
class BrokerBoot : Bootable {
    private lateinit var traceFactory: TraceFactory
    private lateinit var tr: Trace
    private lateinit var myBroker: Broker

    override fun boot(props: ElkoProperties, gorgel: Gorgel, traceFactory: TraceFactory, clock: Clock) {
        val myGorgel = gorgel.getChild(BrokerBoot::class)
        this.traceFactory = traceFactory
        tr = traceFactory.trace("brok")
        val brokerServerGraph = BrokerServerOgd(object : BrokerServerOgd.Provided, ProvidedAdministration() {
            override fun clock() = ConstantDefinition(clock)
            override fun gorgel() = ConstantDefinition(gorgel)
        }, ObjectGraphConfiguration(object : ObjectGraphLogger {
            override fun errorDuringCleanUp(sourceObject: Any, operation: String, exception: Exception) {
                myGorgel.error("Error during cleanup of object graph. Object: $sourceObject, operation: $operation", exception)
            }
        })).Graph()
        val timer = brokerServerGraph.timer()
        val server = Server(props, "broker", tr, timer, clock, traceFactory)
        myBroker = Broker(server, tr, timer, traceFactory, clock)
        if (server.startListeners("conf.listen",
                        BrokerServiceFactory()) == 0) {
            tr.errori("no listeners specified")
        }
        for (service in server.services()) {
            service.setProviderID(0)
            myBroker.addService(service)
        }
    }

    /**
     * Service factory for the Broker.
     *
     * The Broker offers two kinds of service connections:
     *
     * broker/client - for servers being brokered or requesting brokerage
     * broker/admin  - for system administrators
     */
    private inner class BrokerServiceFactory : ServiceFactory {
        override fun provideFactory(label: String,
                                    auth: AuthDesc,
                                    allow: Set<String>,
                                    serviceNames: MutableList<String>,
                                    protocol: String): MessageHandlerFactory {
            var allowClient = false
            var allowAdmin = false
            if (allow.contains("any")) {
                allowClient = true
                allowAdmin = true
            }
            if (allow.contains("admin")) {
                allowAdmin = true
            }
            if (allow.contains("client")) {
                allowClient = true
            }
            if (allowAdmin) {
                serviceNames.add("broker-admin")
            }
            if (allowClient) {
                serviceNames.add("broker-client")
            }
            return BrokerActorFactory(myBroker, auth, allowAdmin,
                    allowClient, tr, traceFactory)
        }
    }
}
