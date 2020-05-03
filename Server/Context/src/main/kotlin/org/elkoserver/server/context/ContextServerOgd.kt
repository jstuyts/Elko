package org.elkoserver.server.context

import org.elkoserver.foundation.timer.Timer
import org.elkoserver.foundation.timer.TimerSgd
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphDefinition
import org.ooverkommelig.providedByMe
import org.ooverkommelig.req

class ContextServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(provided, configuration) {
    interface Provided : ContextServerSgd.Provided, TimerSgd.Provided {
        override fun timer(): D<Timer> = providedByMe()
    }

    val contextServerSgd = add(ContextServerSgd(object : ContextServerSgd.Provided by provided {
        override fun timer(): D<Timer> = timerSgd.timer
    }, configuration))

    val timerSgd = add(TimerSgd(provided, configuration))

    inner class Graph : DefinitionObjectGraph() {
        fun contextor() = req(contextServerSgd.contextor)
    }
}
