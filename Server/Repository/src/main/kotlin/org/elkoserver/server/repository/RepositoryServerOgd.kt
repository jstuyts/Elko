package org.elkoserver.server.repository

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.foundation.server.metadata.ServerMetadataSgd
import org.elkoserver.foundation.timer.timerthread.TimerThreadTimerSgd
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphDefinition
import org.ooverkommelig.req
import java.time.Clock

internal class RepositoryServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(configuration) {
    interface Provided {
        fun baseGorgel(): D<Gorgel>
        fun props(): D<ElkoProperties>
        fun clock(): D<Clock>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
    }
    
    val repositoryServerSgd = add(RepositoryServerSgd(object : RepositoryServerSgd.Provided {
        override fun baseGorgel() = provided.baseGorgel()
        override fun props() = provided.props()
        override fun clock() = provided.clock()
        override fun timer() = timerSgd.timer
        override fun authDescFromPropertiesFactory() = serverMetadataSgd.authDescFromPropertiesFactory
        override fun hostDescFromPropertiesFactory() = serverMetadataSgd.hostDescFromPropertiesFactory
        override fun externalShutdownWatcher(): D<ShutdownWatcher> = provided.externalShutdownWatcher()
    }, configuration))

    val serverMetadataSgd = add(ServerMetadataSgd(object : ServerMetadataSgd.Provided {
        override fun baseGorgel() = provided.baseGorgel()
        override fun props() = provided.props()
    }, configuration))

    val timerSgd = add(TimerThreadTimerSgd(object : TimerThreadTimerSgd.Provided {
        override fun clock() = provided.clock()
        override fun baseGorgel() = provided.baseGorgel()
    }, configuration))

    inner class Graph : DefinitionObjectGraph() {
        fun server() = req(repositoryServerSgd.server)
    }
}
