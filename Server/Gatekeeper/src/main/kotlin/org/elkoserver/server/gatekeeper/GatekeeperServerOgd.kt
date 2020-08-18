package org.elkoserver.server.gatekeeper

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

internal class GatekeeperServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(provided, configuration) {
    interface Provided : GatekeeperServerSgd.Provided, ServerMetadataSgd.Provided, TimerThreadTimerSgd.Provided {
        override fun timer(): D<Timer> = providedByMe()
        override fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory> = providedByMe()
        override fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory> = providedByMe()
    }

    val gatekeeperServerSgd = add(GatekeeperServerSgd(object : GatekeeperServerSgd.Provided by provided {
        override fun authDescFromPropertiesFactory() = serverMetadataSgd.authDescFromPropertiesFactory
        override fun hostDescFromPropertiesFactory() = serverMetadataSgd.hostDescFromPropertiesFactory
        override fun timer() = timerSgd.timer
    }, configuration))

    val serverMetadataSgd = add(ServerMetadataSgd(provided, configuration))

    val timerSgd = add(TimerThreadTimerSgd(provided, configuration))

    inner class Graph : DefinitionObjectGraph() {
        fun server() = req(gatekeeperServerSgd.server)
    }
}
