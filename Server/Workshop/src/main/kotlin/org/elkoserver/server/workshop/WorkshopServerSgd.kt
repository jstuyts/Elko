@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.workshop

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.LongIdGenerator
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

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(WorkshopBoot::class) }

    val startupWorkerListGorgel by Once { req(provided.baseGorgel()).getChild(StartupWorkerList::class) }

    val workshopGorgel by Once { req(provided.baseGorgel()).getChild(Workshop::class) }

    val workshopActorGorgel by Once { req(provided.baseGorgel()).getChild(WorkshopActor::class) }

    val server by Once {
        Server(
                req(provided.props()),
                "workshop",
                req(workTrace),
                req(provided.timer()),
                req(provided.clock()),
                req(provided.traceFactory()),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(serverTagGenerator))
    }
            .init {
                if (it.startListeners("conf.listen", req(workshopServiceFactory)) == 0) {
                    req(bootGorgel).error("no listeners specified")
                } else {
                    req(workshop).loadStartupWorkers()
                }

            }

    val serverTagGenerator by Once { LongIdGenerator() }

    val workshop: D<Workshop> by Once { Workshop(req(server), req(workshopGorgel), req(startupWorkerListGorgel), req(workTrace), req(provided.traceFactory()), req(provided.clock())) }

    val workshopServiceFactory by Once { WorkshopServiceFactory(req(workshop), req(workshopActorGorgel), req(provided.traceFactory())) }
}
