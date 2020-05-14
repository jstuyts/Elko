@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.gatekeeper

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

internal class GatekeeperServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun traceFactory(): D<TraceFactory>
        fun timer(): D<Timer>
        fun clock(): D<Clock>
        fun props(): D<ElkoProperties>
        fun baseGorgel(): D<Gorgel>
    }

    val gateTrace by Once { req(provided.traceFactory()).trace("gate") }

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(GatekeeperBoot::class) }

    val server by Once { Server(req(provided.props()), "gatekeeper", req(gateTrace), req(provided.timer()), req(provided.clock()), req(provided.traceFactory())) }
            .init {
                if (it.startListeners("conf.listen", req(gatekeeperServiceFactory)) == 0) {
                    req(bootGorgel).error("no listeners specified")
                }
            }

    val gatekeeper: D<Gatekeeper> by Once { Gatekeeper(req(server), req(gateTrace), req(provided.timer()), req(provided.traceFactory()), req(provided.clock())) }

    val actionTimeout by Once { 1000 * req(provided.props()).intProperty("conf.gatekeeper.actiontimeout", GatekeeperBoot.DEFAULT_ACTION_TIMEOUT) }

    val gatekeeperServiceFactory by Once { GatekeeperServiceFactory(req(gatekeeper), req(actionTimeout), req(gateTrace), req(provided.timer()), req(provided.traceFactory())) }
}
