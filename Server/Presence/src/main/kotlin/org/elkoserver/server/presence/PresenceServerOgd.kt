package org.elkoserver.server.presence

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.foundation.server.metadata.ServerMetadataSgd
import org.elkoserver.foundation.timer.timerthread.TimerThreadTimerSgd
import org.elkoserver.objectdatabase.propertiesbased.PropertiesBasedObjectDatabaseSgd
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.ConstantDefinition
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphDefinition
import org.ooverkommelig.req
import java.time.Clock

internal class PresenceServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(configuration) {
    interface Provided {
        fun baseGorgel(): D<Gorgel>
        fun props(): D<ElkoProperties>
        fun clock(): D<Clock>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
    }

    val objectDatabaseSgd: PropertiesBasedObjectDatabaseSgd = add(PropertiesBasedObjectDatabaseSgd(object : PropertiesBasedObjectDatabaseSgd.Provided {
        override fun baseGorgel() = provided.baseGorgel()
        override fun classList() = presenceServerSgd.objectDatabaseClassList
        override fun connectionRetrierFactory() = presenceServerSgd.connectionRetrierFactory
        override fun hostDescFromPropertiesFactory() = serverMetadataSgd.hostDescFromPropertiesFactory
        override fun jsonToObjectDeserializer() = presenceServerSgd.jsonToObjectDeserializer
        override fun propRoot() = ConstantDefinition("conf.presence")
        override fun props() = provided.props()
        override fun returnRunner() = presenceServerSgd.runner
        override fun serverName() = presenceServerSgd.serverName
        override fun serviceFinder() = presenceServerSgd.server
    }))

    val presenceServerSgd = add(PresenceServerSgd(object : PresenceServerSgd.Provided {
        override fun baseGorgel() = provided.baseGorgel()
        override fun props() = provided.props()
        override fun clock() = provided.clock()
        override fun timer() = timerSgd.timer
        override fun authDescFromPropertiesFactory() = serverMetadataSgd.authDescFromPropertiesFactory
        override fun hostDescFromPropertiesFactory() = serverMetadataSgd.hostDescFromPropertiesFactory
        override fun externalShutdownWatcher() = provided.externalShutdownWatcher()
        override fun objectDatabase() = objectDatabaseSgd.objectDatabase
    }, configuration))

    val serverMetadataSgd = add(ServerMetadataSgd(object : ServerMetadataSgd.Provided {
        override fun baseGorgel() = provided.baseGorgel()
        override fun props() = provided.props()
    }, configuration))

    val timerSgd = add(TimerThreadTimerSgd(object : TimerThreadTimerSgd.Provided {
        override fun baseGorgel() = provided.baseGorgel()
        override fun clock() = provided.clock()
    }, configuration))

    inner class Graph : DefinitionObjectGraph() {
        fun server() = req(presenceServerSgd.server)
    }
}
