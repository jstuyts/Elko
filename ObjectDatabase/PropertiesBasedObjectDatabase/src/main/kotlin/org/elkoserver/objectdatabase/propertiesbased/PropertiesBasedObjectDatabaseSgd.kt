package org.elkoserver.objectdatabase.propertiesbased

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MessageDispatcherFactory
import org.elkoserver.foundation.json.MethodInvoker
import org.elkoserver.foundation.net.Communication.COMMUNICATION_CATEGORY_TAG
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.ServiceFinder
import org.elkoserver.idgeneration.LongIdGenerator
import org.elkoserver.objectdatabase.DirectObjectDatabaseFactory
import org.elkoserver.objectdatabase.DirectObjectDatabaseRunnerFactory
import org.elkoserver.objectdatabase.GetRequestFactory
import org.elkoserver.objectdatabase.ObjectDatabaseDirect
import org.elkoserver.objectdatabase.ObjectDatabaseRepository
import org.elkoserver.objectdatabase.ObjectDatabaseRepositoryActor
import org.elkoserver.objectdatabase.PutRequestFactory
import org.elkoserver.objectdatabase.QueryRequestFactory
import org.elkoserver.objectdatabase.RemoveRequestFactory
import org.elkoserver.objectdatabase.RepositoryObjectDatabaseFactory
import org.elkoserver.objectdatabase.UpdateRequestFactory
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
            DirectObjectDatabaseConfiguration -> req(directObjectDatabaseFactory)
            RepositoryObjectDatabaseConfiguration -> req(repositoryObjectDatabaseFactory)
        }
    }

    val objectDatabase by Once {
        req(objectDatabaseFactory).create().apply {
            req(provided.classList()).forEach(this::addClass)
        }
    }

    private val directObjectDatabaseGorgel by Once { req(provided.baseGorgel()).getChild(ObjectDatabaseDirect::class) }

    private val directObjectDatabaseRunnerFactory by Once { DirectObjectDatabaseRunnerFactory() }

    private val directObjectDatabaseFactory by Once {
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

    private val repositoryObjectDatabaseGorgel by Once { req(provided.baseGorgel()).getChild(ObjectDatabaseRepository::class) }

    private val odbActorGorgel by Once {
        req(provided.baseGorgel()).getChild(
            ObjectDatabaseRepositoryActor::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }

    private val methodInvokerCommGorgel by Once {
        req(provided.baseGorgel()).getChild(MethodInvoker::class).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG)
    }

    private val messageDispatcherFactory by Once {
        MessageDispatcherFactory(
            req(methodInvokerCommGorgel),
            req(provided.jsonToObjectDeserializer())
        )
    }

    private val requestTagGenerator by Once { LongIdGenerator(1L) }

    private val getRequestFactory by Once { GetRequestFactory(req(requestTagGenerator)) }

    private val putRequestFactory by Once { PutRequestFactory(req(requestTagGenerator)) }

    private val updateRequestFactory by Once { UpdateRequestFactory(req(requestTagGenerator)) }

    private val queryRequestFactory by Once { QueryRequestFactory(req(requestTagGenerator)) }

    private val removeRequestFactory by Once { RemoveRequestFactory(req(requestTagGenerator)) }

    private val mustSendDebugReplies by Once { req(provided.props()).testProperty("conf.msgdiagnostics") }

    private val repositoryObjectDatabaseFactory by Once {
        RepositoryObjectDatabaseFactory(
            req(provided.serviceFinder()),
            req(provided.serverName()),
            req(provided.props()),
            req(provided.propRoot()),
            req(repositoryObjectDatabaseGorgel),
            req(odbActorGorgel),
            req(messageDispatcherFactory),
            req(provided.hostDescFromPropertiesFactory()),
            req(provided.jsonToObjectDeserializer()),
            req(getRequestFactory),
            req(putRequestFactory),
            req(updateRequestFactory),
            req(queryRequestFactory),
            req(removeRequestFactory),
            req(mustSendDebugReplies),
            req(provided.connectionRetrierFactory())
        )
    }
}
