package org.elkoserver.objectdatabase.propertiesbased

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.ServiceFinder
import org.elkoserver.objectdatabase.DirectObjectDatabaseSgd
import org.elkoserver.objectdatabase.RepositoryObjectDatabaseSgd
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req
import java.util.concurrent.Executor

class PropertiesBasedObjectDatabaseSgd(
    provided: Provided,
    objectGraphConfiguration: ObjectGraphConfiguration = ObjectGraphConfiguration()
) : SubGraphDefinition(objectGraphConfiguration) {
    interface Provided {
        fun baseGorgel(): D<Gorgel>
        fun classList(): D<Map<String, Class<*>>>
        fun connectionRetrierFactory(): D<ConnectionRetrierFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
        fun jsonToObjectDeserializer(): D<JsonToObjectDeserializer>
        fun propRoot(): D<String>
        fun props(): D<ElkoProperties>
        fun returnRunner(): D<Executor>
        fun serverName(): D<String>
        fun serviceFinder(): D<ServiceFinder>
    }

    private val objectDatabaseConfigurationFromPropertiesFactory by Once { ObjectDatabaseConfigurationFromPropertiesFactory(req(provided.props()), req(provided.propRoot())) }

    private val objectDatabaseFactory by Once {
        when (req(objectDatabaseConfigurationFromPropertiesFactory).read()) {
            DirectObjectDatabaseConfiguration -> req(directObjectDatabaseSgd.directObjectDatabaseFactory)
            RepositoryObjectDatabaseConfiguration -> req(objectDatabaseRepositorySgd.repositoryObjectDatabaseFactory)
        }
    }

    val objectDatabase by Once {
        req(objectDatabaseFactory).create().apply {
            req(provided.classList()).forEach(this::addClass)
        }
    }
        .dispose { it.shutDown() }

    private val directObjectDatabaseSgd = add(DirectObjectDatabaseSgd(object: DirectObjectDatabaseSgd.Provided {
        override fun baseGorgel() = provided.baseGorgel()
        override fun connectionRetrierFactory() = provided.connectionRetrierFactory()
        override fun hostDescFromPropertiesFactory() = provided.hostDescFromPropertiesFactory()
        override fun jsonToObjectDeserializer() = provided.jsonToObjectDeserializer()
        override fun propRoot() = provided.propRoot()
        override fun props() = provided.props()
        override fun returnRunner() = provided.returnRunner()
        override fun serverName() = provided.serverName()
    }, objectGraphConfiguration))

    private val objectDatabaseRepositorySgd = add(RepositoryObjectDatabaseSgd(object : RepositoryObjectDatabaseSgd.Provided {
        override fun baseGorgel() = provided.baseGorgel()
        override fun connectionRetrierFactory() = provided.connectionRetrierFactory()
        override fun hostDescFromPropertiesFactory() = provided.hostDescFromPropertiesFactory()
        override fun jsonToObjectDeserializer() = provided.jsonToObjectDeserializer()
        override fun propRoot() = provided.propRoot()
        override fun props() = provided.props()
        override fun serverName() = provided.serverName()
        override fun serviceFinder() = provided.serviceFinder()
    }, objectGraphConfiguration))
}
