package org.elkoserver.objectdatabase

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req
import java.util.concurrent.Executor

class DirectObjectDatabaseSgd(
    provided: Provided,
    objectGraphConfiguration: ObjectGraphConfiguration = ObjectGraphConfiguration()
) : SubGraphDefinition(objectGraphConfiguration) {
    interface Provided {
        fun baseGorgel(): D<Gorgel>
        fun connectionRetrierFactory(): D<ConnectionRetrierFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
        fun jsonToObjectDeserializer(): D<JsonToObjectDeserializer>
        fun propRoot(): D<String>
        fun props(): D<ElkoProperties>
        fun returnRunner(): D<Executor>
        fun serverName(): D<String>
    }
    private val directObjectDatabaseGorgel by Once { req(provided.baseGorgel()).getChild(ObjectDatabaseDirect::class) }

    private val directObjectDatabaseRunnerFactory by Once { DirectObjectDatabaseRunnerFactory() }

    val directObjectDatabaseFactory by Once {
        DirectObjectDatabaseFactory(
            req(provided.props()),
            req(provided.propRoot()),
            req(directObjectDatabaseGorgel),
            req(directObjectDatabaseRunnerFactory),
            req(provided.baseGorgel()),
            req(provided.jsonToObjectDeserializer()),
            req(provided.returnRunner())
        )
    }
}
