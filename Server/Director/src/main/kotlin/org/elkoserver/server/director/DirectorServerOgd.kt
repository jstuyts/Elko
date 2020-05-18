package org.elkoserver.server.director

import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.ServerMetadataSgd
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.foundation.timer.TimerSgd
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphDefinition
import org.ooverkommelig.providedByMe
import org.ooverkommelig.req

internal class DirectorServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(provided, configuration) {
    interface Provided : DirectorServerSgd.Provided, ServerMetadataSgd.Provided, TimerSgd.Provided {
        override fun timer(): D<Timer> = providedByMe()
        override fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory> = providedByMe()
        override fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory> = providedByMe()
    }

    val directorServerSgd = add(DirectorServerSgd(object : DirectorServerSgd.Provided by provided {
        override fun timer() = timerSgd.timer
        override fun authDescFromPropertiesFactory() = serverMetadataSgd.authDescFromPropertiesFactory
        override fun hostDescFromPropertiesFactory() = serverMetadataSgd.hostDescFromPropertiesFactory
    }))

    val serverMetadataSgd = add(ServerMetadataSgd(provided))

    val timerSgd = add(TimerSgd(provided))

    inner class Graph : DefinitionObjectGraph() {
        fun server() = req(directorServerSgd.server)
    }
}
