@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.workshop

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
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
    }

    val workTrace by Once { req(provided.traceFactory()).trace("work") }

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(WorkshopBoot::class) }

    val server by Once { Server(req(provided.props()), "workshop", req(workTrace), req(provided.timer()), req(provided.clock()), req(provided.traceFactory())) }
            .init {
                if (it.startListeners("conf.listen", req(workshopServiceFactory)) == 0) {
                    req(bootGorgel).error("no listeners specified")
                } else {
                    req(workshop).loadStartupWorkers()
                }

            }

    val workshop: D<Workshop> by Once { Workshop(req(server), req(workTrace), req(provided.traceFactory()), req(provided.clock())) }

    val workshopServiceFactory by Once { WorkshopServiceFactory(req(workshop), req(workTrace), req(provided.traceFactory())) }
}
