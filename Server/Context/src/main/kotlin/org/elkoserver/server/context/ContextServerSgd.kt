@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.context

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.TraceFactory
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
        fun timer(): D<Timer>
        fun traceFactory(): D<TraceFactory>
    }

    val contTrace by Once { req(provided.traceFactory()).trace("cont") }

    val contextServiceFactory by Once { ContextServiceFactory(req(contextor), req(contTrace), req(provided.traceFactory()), req(provided.timer())) }
            .init {
                if (req(server).startListeners("conf.listen", it) == 0) {
                    // FIXME: Do not use "fatalError" as this exits the process hard.
                    throw IllegalStateException("no listeners specified")
                }
                // This must run after the listeners of the server have been started.
                val contextor = req(contextor)
                contextor.registerWithDirectors(req(directors), req(serverListeners))
                contextor.registerWithPresencers(req(presencers))
            }
            .eager()

    val server by Once { Server(req(provided.props()), "context", req(contTrace), req(provided.timer()), req(provided.clock()), req(provided.traceFactory())) }

    val serverListeners by Once { req(server).listeners() }

    val objectDatabase by Once {
        req(server).openObjectDatabase("conf.context")!!.apply {
            addClass("context", Context::class.java)
            addClass("item", Item::class.java)
            addClass("user", User::class.java)
            addClass("serverdesc", ServerDesc::class.java)
        }
    }

    val contextorEntryTimeout by Once {
        req(provided.props()).intProperty("conf.context.entrytimeout", DEFAULT_ENTER_TIMEOUT_IN_SECONDS)
    }

    val contextorLimit by Once {
        req(provided.props()).intProperty("conf.context.userlimit", 0)
    }

    val contextor by Once { Contextor(req(objectDatabase), req(server), req(contTrace), req(provided.timer()), req(provided.traceFactory()), req(provided.clock()), req(contextorEntryTimeout), req(contextorLimit)) }

    val directors by Once { scanHostList(req(provided.props()), "conf.register", req(provided.traceFactory())) }

    val presencers by Once { scanHostList(req(provided.props()), "conf.presence", req(provided.traceFactory())) }

    companion object {
        private const val DEFAULT_ENTER_TIMEOUT_IN_SECONDS = 15
    }
}
