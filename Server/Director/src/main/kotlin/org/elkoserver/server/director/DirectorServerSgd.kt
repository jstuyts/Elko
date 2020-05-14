@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.director

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

internal class DirectorServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun traceFactory(): D<TraceFactory>
        fun timer(): D<Timer>
        fun clock(): D<Clock>
        fun props(): D<ElkoProperties>
        fun baseGorgel(): D<Gorgel>
    }
    
    val direTrace by Once { req(provided.traceFactory()).trace("dire") }

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(DirectorBoot::class) }
    
    val server by Once { Server(req(provided.props()), "director", req(direTrace), req(provided.timer()), req(provided.clock()), req(provided.traceFactory()))  }
            .init {
                if (it.startListeners("conf.listen", req(directorServiceFactory)) == 0) {
                    req(bootGorgel).error("no listeners specified")
                }
            }

    val director: D<Director> by Once { Director(req(server), req(direTrace), req(provided.traceFactory()), req(provided.clock())) }

    val directorServiceFactory by Once { DirectorServiceFactory(req(director), req(direTrace), req(provided.traceFactory())) }
}
