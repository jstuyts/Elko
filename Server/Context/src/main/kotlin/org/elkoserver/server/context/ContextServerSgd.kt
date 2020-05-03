@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.context

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

class ContextServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun clock(): D<Clock>
        fun props(): D<ElkoProperties>
        fun rootGorgel(): D<Gorgel>
        fun traceFactory(): D<TraceFactory>
    }

    val contTrace by Once { req(provided.traceFactory()).trace("cont") }

    val timer by Once { Timer(req(provided.traceFactory()), req(provided.clock())) }

    val contextServiceFactory by Once { ContextServiceFactory(req(contextor), req(contTrace), req(provided.traceFactory()), req(timer)) }
            .init {
                if (req(server).startListeners("conf.listen", it) == 0) {
                    // FIXME: Do not use "fatalError" as this exits the process hard.
                    req(contTrace).fatalError("no listeners specified")
                }
                // FIXME: Must this happen after starting the listeners? If not, move to initialization of "contextor".
                val contextor = req(contextor)
                contextor.registerWithDirectors(req(directors), req(serverListeners))
                contextor.registerWithPresencers(req(presencers))
            }
            .eager()

    val server by Once { Server(req(provided.props()), "context", req(contTrace), req(timer), req(provided.clock()), req(provided.traceFactory())) }

    val serverListeners by Once { req(server).listeners() }

    val contextor by Once { Contextor(req(server), req(contTrace), req(timer), req(provided.traceFactory()), req(provided.clock()))}

    val directors by Once { scanHostList(req(provided.props()), "conf.register", req(provided.traceFactory()))}

    val presencers by Once { scanHostList(req(provided.props()), "conf.presence", req(provided.traceFactory()))}
}
