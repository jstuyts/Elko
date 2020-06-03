package org.elkoserver.server.broker

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.ordinalgeneration.LongOrdinalGenerator
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Service factory for the Broker.
 *
 * The Broker offers two kinds of service connections:
 *
 * broker/client - for servers being brokered or requesting brokerage
 * broker/admin  - for system administrators
 */
internal class BrokerServiceFactory(private val broker: Broker, private val brokerActorGorgel: Gorgel, private val traceFactory: TraceFactory, private val clientOrdinalGenerator: LongOrdinalGenerator) : ServiceFactory {
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
        return BrokerActorFactory(broker, auth, allowAdmin, allowClient, brokerActorGorgel, traceFactory, clientOrdinalGenerator)
    }
}
