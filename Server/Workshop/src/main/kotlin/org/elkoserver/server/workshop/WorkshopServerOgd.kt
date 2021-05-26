package org.elkoserver.server.workshop

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.foundation.server.metadata.ServerMetadataSgd
import org.elkoserver.foundation.timer.timerthread.TimerThreadTimerSgd
import org.elkoserver.objectdatabase.ObjectDatabase
import org.elkoserver.objectdatabase.propertiesbased.PropertiesBasedObjectDatabaseSgd
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.ConstantDefinition
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphDefinition
import org.ooverkommelig.req
import java.time.Clock

internal class WorkshopServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(configuration) {
    interface Provided {
        fun baseGorgel(): D<Gorgel>
        fun props(): D<ElkoProperties>
        fun clock(): D<Clock>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
    }

    val objectDatabaseSgd: PropertiesBasedObjectDatabaseSgd = add(PropertiesBasedObjectDatabaseSgd(object : PropertiesBasedObjectDatabaseSgd.Provided {
        override fun baseGorgel() = provided.baseGorgel()
        override fun classList() = workshopServerSgd.objectDatabaseClassList
        override fun connectionRetrierFactory() = workshopServerSgd.connectionRetrierFactory
        override fun hostDescFromPropertiesFactory() = serverMetadataSgd.hostDescFromPropertiesFactory
        override fun jsonToObjectDeserializer() = workshopServerSgd.jsonToObjectDeserializer
        override fun propRoot() = ConstantDefinition("conf.workshop")
        override fun props() = provided.props()
        override fun returnRunner() = workshopServerSgd.runner
        override fun serverName() = workshopServerSgd.serverName
        override fun serviceFinder() = workshopServerSgd.server
    }))

    val workshopServerSgd = add(WorkshopServerSgd(object : WorkshopServerSgd.Provided {
        override fun props() = provided.props()
        override fun baseGorgel() = provided.baseGorgel()
        override fun clock() = provided.clock()
        override fun authDescFromPropertiesFactory() = serverMetadataSgd.authDescFromPropertiesFactory
        override fun hostDescFromPropertiesFactory() = serverMetadataSgd.hostDescFromPropertiesFactory
        override fun timer() = timerSgd.timer
        override fun externalShutdownWatcher() = provided.externalShutdownWatcher()
        override fun workerDatabases() = ConstantDefinition<Map<String, ObjectDatabase>>(emptyMap())
        override fun objectDatabase() = objectDatabaseSgd.objectDatabase
    }, configuration))

    val serverMetadataSgd = add(ServerMetadataSgd(object : ServerMetadataSgd.Provided {
        override fun props() = provided.props()
        override fun baseGorgel() = provided.baseGorgel()
    }, configuration))

    val timerSgd = add(TimerThreadTimerSgd(object : TimerThreadTimerSgd.Provided {
        override fun baseGorgel() = provided.baseGorgel()
        override fun clock() = provided.clock()
    }, configuration))

    inner class Graph : DefinitionObjectGraph() {
        fun server() = req(workshopServerSgd.server)
    }
}
