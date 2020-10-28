package org.elkoserver.server.context

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

internal class ContextServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(configuration) {
    interface Provided {
        fun clock(): D<Clock>
        fun props(): D<ElkoProperties>
        fun baseGorgel(): D<Gorgel>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
    }

    val contextServerSgd = add(ContextServerSgd(object : ContextServerSgd.Provided {
        override fun clock() = provided.clock()
        override fun props() = provided.props()
        override fun baseGorgel() = provided.baseGorgel()
        override fun externalShutdownWatcher() = provided.externalShutdownWatcher()
        override fun timer() = timerSgd.timer
        override fun authDescFromPropertiesFactory() = serverMetadataSgd.authDescFromPropertiesFactory
        override fun hostDescFromPropertiesFactory() = serverMetadataSgd.hostDescFromPropertiesFactory
    }, configuration))

    val serverMetadataSgd = add(ServerMetadataSgd(object : ServerMetadataSgd.Provided {
        override fun props() = provided.props()
        override fun baseGorgel() = provided.baseGorgel()
    }, configuration))

    val timerSgd = add(TimerThreadTimerSgd(object : TimerThreadTimerSgd.Provided {
        override fun clock() = provided.clock()
        override fun baseGorgel() = provided.baseGorgel()
    }, configuration))

    inner class Graph : DefinitionObjectGraph() {
        fun contextor() = req(contextServerSgd.contextor)
    }
}
