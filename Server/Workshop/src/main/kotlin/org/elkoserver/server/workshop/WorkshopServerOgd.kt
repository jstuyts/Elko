package org.elkoserver.server.workshop

import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.ServerMetadataSgd
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.foundation.timer.timerthread.TimerThreadTimerSgd
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphDefinition
import org.ooverkommelig.providedByMe
import org.ooverkommelig.req

internal class WorkshopServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(provided, configuration) {
    interface Provided : WorkshopServerSgd.Provided, ServerMetadataSgd.Provided, TimerThreadTimerSgd.Provided {
        override fun timer(): D<Timer> = providedByMe()
        override fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory> = providedByMe()
        override fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory> = providedByMe()
    }

    val workshopServerSgd = add(WorkshopServerSgd(object : WorkshopServerSgd.Provided by provided {
        override fun authDescFromPropertiesFactory() = serverMetadataSgd.authDescFromPropertiesFactory
        override fun hostDescFromPropertiesFactory() = serverMetadataSgd.hostDescFromPropertiesFactory
        override fun timer() = timerSgd.timer
    }, configuration))

    val serverMetadataSgd = add(ServerMetadataSgd(provided, configuration))

    val timerSgd = add(TimerThreadTimerSgd(provided, configuration))

    inner class Graph : DefinitionObjectGraph() {
        fun server() = req(workshopServerSgd.server)
    }
}
