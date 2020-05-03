package org.elkoserver.server.director

import org.elkoserver.foundation.timer.TimerSgd
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphDefinition
import org.ooverkommelig.req

class DirectorServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(provided, configuration) {
    interface Provided : TimerSgd.Provided

    val timerSgd = add(TimerSgd(provided))

    inner class Graph : DefinitionObjectGraph() {
        fun timer() = req(timerSgd.timer)
    }
}
