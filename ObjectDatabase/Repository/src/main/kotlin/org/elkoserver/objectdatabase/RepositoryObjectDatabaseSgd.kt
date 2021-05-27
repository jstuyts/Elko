package org.elkoserver.objectdatabase

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MessageDispatcherFactory
import org.elkoserver.foundation.json.MethodInvoker
import org.elkoserver.foundation.net.Communication.COMMUNICATION_CATEGORY_TAG
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.ServiceFinder
import org.elkoserver.idgeneration.LongIdGenerator
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ParameterizedOnce
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req

class RepositoryObjectDatabaseSgd(
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
        fun serverName(): D<String>
        fun serviceFinder(): D<ServiceFinder>
    }
    private val repositoryObjectDatabaseGorgel by Once { req(provided.baseGorgel()).getChild(ObjectDatabaseRepository::class) }

    private val serviceLookupRepositoryHostInitializerGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLookupRepositoryHostInitializer::class) }

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

    private val repositoryPropRoot by Once { "${req(provided.propRoot())}.repository" }

    private val repositoryHostInitializer by Once {
        val serviceName = req(provided.props()).getProperty("${req(this.repositoryPropRoot)}.service")
        if (serviceName != null) {
            req(serviceLookupRepositoryHostInitializer(serviceName))
        } else {
            req(hostFromPropertiesRepositoryHostInitializer)
        }
    }

    private val serviceLookupRepositoryHostInitializer by ParameterizedOnce { serviceName: String ->
        ServiceLookupRepositoryHostInitializer(
            req(provided.props()),
            req(repositoryPropRoot),
            serviceName,
            req(provided.serviceFinder()),
            req(serviceLookupRepositoryHostInitializerGorgel)
        )
    }

    private val hostFromPropertiesRepositoryHostInitializer by Once {
        HostFromPropertiesRepositoryHostInitializer(req(provided.hostDescFromPropertiesFactory()), req(repositoryPropRoot))
    }

    val repositoryObjectDatabaseFactory by Once {
        RepositoryObjectDatabaseFactory(
            req(provided.serverName()),
            req(provided.props()),
            req(provided.propRoot()),
            req(repositoryObjectDatabaseGorgel),
            req(odbActorGorgel),
            req(messageDispatcherFactory),
            req(provided.jsonToObjectDeserializer()),
            req(getRequestFactory),
            req(putRequestFactory),
            req(updateRequestFactory),
            req(queryRequestFactory),
            req(removeRequestFactory),
            req(mustSendDebugReplies),
            req(provided.connectionRetrierFactory()),
            req(repositoryHostInitializer)
        )
    }
}
