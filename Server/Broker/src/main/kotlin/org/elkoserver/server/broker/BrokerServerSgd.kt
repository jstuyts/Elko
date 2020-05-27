@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.broker

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.BaseConnectionSetup
import org.elkoserver.foundation.server.LoadWatcher
import org.elkoserver.foundation.server.LongIdGenerator
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServerLoadMonitor
import org.elkoserver.foundation.server.ServiceActor
import org.elkoserver.foundation.server.ServiceLink
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

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class)}

    val brokerBootGorgel by Once { req(provided.baseGorgel()).getChild(BrokerBoot::class) }

    val brokerGorgel by Once { req(provided.baseGorgel()).getChild(Broker::class) }

    val brokerActorGorgel by Once { req(provided.baseGorgel()).getChild(BrokerActor::class) }

    val launcherTableGorgel by Once { req(provided.baseGorgel()).getChild(LauncherTable::class) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val server by Once {
        Server(
                req(provided.props()),
                "broker",
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(serviceActorGorgel),
                req(baseConnectionSetupGorgel),
                req(brokTrace),
                req(provided.timer()),
                req(provided.clock()),
                req(provided.traceFactory()),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(serverTagGenerator),
                req(serverLoadMonitor))
    }
            .init {
                if (it.startListeners("conf.listen", req(brokerServiceFactory)) == 0) {
                    req(brokerBootGorgel).error("no listeners specified")
                }
                for (service in it.services()) {
                    service.providerID = 0
                    req(broker).addService(service)
                }
            }

    val serverLoadMonitor by Once {
        ServerLoadMonitor(
                req(provided.timer()),
                req(provided.clock()),
                req(provided.props()).intProperty("conf.load.time", ServerLoadMonitor.DEFAULT_LOAD_SAMPLE_TIMEOUT) * 1000)
    }
            .init {
                if (req(provided.props()).testProperty("conf.load.log")) {
                    it.registerLoadWatcher(object : LoadWatcher {
                        override fun noteLoadSample(loadFactor: Double) {
                            req(serverLoadMonitorGorgel).d?.run { debug("Load $loadFactor") }
                        }
                    })
                }
            }

    val serverTagGenerator by Once { LongIdGenerator() }

    val startMode by Once {
        when (val startModeStr = req(provided.props()).getProperty("conf.broker.startmode")) {
            null, "initial" -> LauncherTable.START_INITIAL
            "recover" -> LauncherTable.START_RECOVER
            "restart" -> LauncherTable.START_RESTART
            else -> {
                // FIXME: Use another Gorgel
                req(brokerGorgel).error("unknown startmode value '$startModeStr'")
                LauncherTable.START_RECOVER
            }
        }
    }

    val broker: D<Broker> by Once {
        Broker(
                req(server),
                req(brokerGorgel),
                req(launcherTableGorgel),
                req(provided.timer()),
                req(provided.traceFactory()),
                req(provided.clock()),
                req(startMode))
    }

    val brokerServiceFactory by Once { BrokerServiceFactory(req(broker), req(brokerActorGorgel), req(provided.traceFactory())) }
}
