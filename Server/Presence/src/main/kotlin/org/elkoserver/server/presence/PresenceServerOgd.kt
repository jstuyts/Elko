package org.elkoserver.server.presence

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

internal class PresenceServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(provided, configuration) {
    interface Provided : PresenceServerSgd.Provided, ServerMetadataSgd.Provided, TimerThreadTimerSgd.Provided {
        override fun timer(): D<Timer> = providedByMe()
        override fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory> = providedByMe()
        override fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory> = providedByMe()
    }

    val presenceServerSgd = add(PresenceServerSgd(object : PresenceServerSgd.Provided by provided {
        override fun timer() = timerSgd.timer
        override fun authDescFromPropertiesFactory() = serverMetadataSgd.authDescFromPropertiesFactory
        override fun hostDescFromPropertiesFactory() = serverMetadataSgd.hostDescFromPropertiesFactory
    }, configuration))

    val serverMetadataSgd = add(ServerMetadataSgd(provided, configuration))

    val timerSgd = add(TimerThreadTimerSgd(provided, configuration))

    inner class Graph : DefinitionObjectGraph() {
        fun server() = req(presenceServerSgd.server)
    }
}
