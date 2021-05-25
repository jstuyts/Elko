package org.elkoserver.objectdatabase

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MessageDispatcherFactory
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.ServiceFinder
import org.elkoserver.util.trace.slf4j.Gorgel

class RepositoryObjectDatabaseFactory(
        private val serviceFinder: ServiceFinder,
        private val serverName: String,
        private val myProps: ElkoProperties,
        private val propRoot: String,
        private val repositoryObjectDatabaseGorgel: Gorgel,
        private val odbActorGorgel: Gorgel,
        private val messageDispatcherFactory: MessageDispatcherFactory,
        private val hostDescFromPropertiesFactory: HostDescFromPropertiesFactory,
        private val jsonToObjectDeserializer: JsonToObjectDeserializer,
        private val getRequestFactory: GetRequestFactory,
        private val putRequestFactory: PutRequestFactory,
        private val updateRequestFactory: UpdateRequestFactory,
        private val queryRequestFactory: QueryRequestFactory,
        private val removeRequestFactory: RemoveRequestFactory,
        private val mustSendDebugReplies: Boolean,
        private val connectionRetrierFactory: ConnectionRetrierFactory): ObjectDatabaseFactory {
    override fun create() =
        ObjectDatabaseRepository(
            serviceFinder,
            serverName,
            myProps,
            propRoot,
            repositoryObjectDatabaseGorgel,
            odbActorGorgel,
            messageDispatcherFactory,
            hostDescFromPropertiesFactory,
            jsonToObjectDeserializer,
            getRequestFactory,
            putRequestFactory,
            updateRequestFactory,
            queryRequestFactory,
            removeRequestFactory,
            mustSendDebugReplies,
            connectionRetrierFactory)
}
