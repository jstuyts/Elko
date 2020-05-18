@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.broker

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req
import java.time.Clock

internal class BrokerServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun traceFactory(): D<TraceFactory>
        fun timer(): D<Timer>
        fun props(): D<ElkoProperties>
        fun clock(): D<Clock>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
    }

    val brokTrace by Once { req(provided.traceFactory()).trace("brok") }

    val brokerBootGorgel by Once { req(provided.baseGorgel()).getChild(BrokerBoot::class) }

    val brokerGorgel by Once { req(provided.baseGorgel()).getChild(Broker::class) }

    val brokerActorGorgel by Once { req(provided.baseGorgel()).getChild(BrokerActor::class) }

    val launcherTableGorgel by Once { req(provided.baseGorgel()).getChild(LauncherTable::class) }

    val server by Once {
        Server(
                req(provided.props()),
                "broker",
                req(brokTrace),
                req(provided.timer()),
                req(provided.clock()),
                req(provided.traceFactory()),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()))
    }
            .init {
                if (it.startListeners("conf.listen", req(brokerServiceFactory)) == 0) {
                    req(brokerBootGorgel).error("no listeners specified")
                }
                for (service in it.services()) {
                    service.setProviderID(0)
                    req(broker).addService(service)
                }
            }

    val broker: D<Broker> by Once { Broker(req(server), req(brokerGorgel), req(launcherTableGorgel), req(provided.timer()), req(provided.traceFactory()), req(provided.clock())) }

    val brokerServiceFactory by Once { BrokerServiceFactory(req(broker), req(brokerActorGorgel), req(provided.traceFactory())) }
}
