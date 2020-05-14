package org.elkoserver.server.director

import org.elkoserver.foundation.timer.Timer
import org.elkoserver.foundation.timer.TimerSgd
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphDefinition
import org.ooverkommelig.providedByMe
import org.ooverkommelig.req

internal class DirectorServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(provided, configuration) {
    interface Provided : DirectorServerSgd.Provided, TimerSgd.Provided {
        override fun timer(): D<Timer> = providedByMe()
    }

    val directorServerSgd = add(DirectorServerSgd(object : DirectorServerSgd.Provided by provided {
        override fun timer() = timerSgd.timer
    }))

    val timerSgd = add(TimerSgd(provided))

    inner class Graph : DefinitionObjectGraph() {
        fun server() = req(directorServerSgd.server)
    }
}
