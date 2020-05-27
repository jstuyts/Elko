@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.workshop

import org.elkoserver.foundation.net.ConnectionRetrier
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.BaseConnectionSetup
import org.elkoserver.foundation.server.LoadWatcher
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServerLoadMonitor
import org.elkoserver.foundation.server.ServiceActor
import org.elkoserver.foundation.server.ServiceLink
import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.LongIdGenerator
import org.elkoserver.idgeneration.RandomIdGenerator
import org.elkoserver.objdb.ObjDBLocal
import org.elkoserver.objdb.ObjDBRemote
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req
import java.security.SecureRandom
import java.time.Clock

internal class WorkshopServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun traceFactory(): D<TraceFactory>
        fun props(): D<ElkoProperties>
        fun timer(): D<Timer>
        fun clock(): D<Clock>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
    }

    val workTrace by Once { req(provided.traceFactory()).trace("work") }

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class) }

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(WorkshopBoot::class) }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val objDbLocalGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBLocal::class) }

    val objDbRemoteGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBRemote::class) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val startupWorkerListGorgel by Once { req(provided.baseGorgel()).getChild(StartupWorkerList::class) }

    val workshopGorgel by Once { req(provided.baseGorgel()).getChild(Workshop::class) }

    val workshopActorGorgel by Once { req(provided.baseGorgel()).getChild(WorkshopActor::class) }

    val server by Once {
        Server(
                req(provided.props()),
                "workshop",
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(serviceActorGorgel),
                req(baseConnectionSetupGorgel),
                req(objDbLocalGorgel),
                req(objDbRemoteGorgel),
                req(provided.baseGorgel()),
                req(connectionRetrierWithoutLabelGorgel),
                req(workTrace),
                req(provided.timer()),
                req(provided.clock()),
                req(provided.traceFactory()),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(serverTagGenerator),
                req(serverLoadMonitor),
                req(sessionIdGenerator))
    }
            .init {
                if (it.startListeners("conf.listen", req(workshopServiceFactory)) == 0) {
                    req(bootGorgel).error("no listeners specified")
                } else {
                    req(workshop).loadStartupWorkers(req(provided.props()).getProperty("conf.workshop.workers"))
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

    val sessionIdGenerator by Once { RandomIdGenerator(req(sessionIdRandom)) }

    val sessionIdRandom by Once { SecureRandom() }
            .init { it.nextBoolean() }

    val serverTagGenerator by Once { LongIdGenerator() }

    val workshop: D<Workshop> by Once { Workshop(req(server), req(workshopGorgel), req(startupWorkerListGorgel), req(workTrace), req(provided.traceFactory()), req(provided.clock())) }

    val workshopServiceFactory by Once { WorkshopServiceFactory(req(workshop), req(workshopActorGorgel), req(provided.traceFactory())) }
}
