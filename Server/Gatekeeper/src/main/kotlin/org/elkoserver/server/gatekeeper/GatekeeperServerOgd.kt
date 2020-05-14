package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.timer.Timer
import org.elkoserver.foundation.timer.TimerSgd
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphDefinition
import org.ooverkommelig.providedByMe
import org.ooverkommelig.req

internal class GatekeeperServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(provided, configuration) {
    interface Provided : GatekeeperServerSgd.Provided, TimerSgd.Provided {
        override fun timer(): D<Timer> = providedByMe()
    }

    val gatekeeperServerSgd = add(GatekeeperServerSgd(object : GatekeeperServerSgd.Provided by provided {
        override fun timer() = timerSgd.timer
    }))

    val timerSgd = add(TimerSgd(provided))

    inner class Graph : DefinitionObjectGraph() {
        fun server() = req(gatekeeperServerSgd.server)
    }
}
