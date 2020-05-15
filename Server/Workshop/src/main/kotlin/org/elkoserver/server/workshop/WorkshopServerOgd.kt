package org.elkoserver.server.workshop

import org.elkoserver.foundation.timer.Timer
import org.elkoserver.foundation.timer.TimerSgd
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphDefinition
import org.ooverkommelig.providedByMe
import org.ooverkommelig.req

internal class WorkshopServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(provided, configuration) {
    interface Provided : WorkshopServerSgd.Provided, TimerSgd.Provided {
        override fun timer(): D<Timer> = providedByMe()
    }

    val workshopServerSgd = add(WorkshopServerSgd(object : WorkshopServerSgd.Provided by provided {
        override fun timer() = timerSgd.timer
    }))

    val timerSgd = add(TimerSgd(provided))

    inner class Graph : DefinitionObjectGraph() {
        fun server() = req(workshopServerSgd.server)
    }
}
