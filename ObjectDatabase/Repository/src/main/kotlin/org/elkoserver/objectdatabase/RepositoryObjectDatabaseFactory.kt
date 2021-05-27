package org.elkoserver.objectdatabase

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MessageDispatcherFactory
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.util.trace.slf4j.Gorgel

class RepositoryObjectDatabaseFactory(
    private val serverName: String,
    private val myProps: ElkoProperties,
    private val propRoot: String,
    private val repositoryObjectDatabaseGorgel: Gorgel,
    private val odbActorGorgel: Gorgel,
    private val messageDispatcherFactory: MessageDispatcherFactory,
    private val jsonToObjectDeserializer: JsonToObjectDeserializer,
    private val getRequestFactory: GetRequestFactory,
    private val putRequestFactory: PutRequestFactory,
    private val updateRequestFactory: UpdateRequestFactory,
    private val queryRequestFactory: QueryRequestFactory,
    private val removeRequestFactory: RemoveRequestFactory,
    private val mustSendDebugReplies: Boolean,
    private val connectionRetrierFactory: ConnectionRetrierFactory,
    private val repositoryHostInitializer: RepositoryHostInitializer
) : ObjectDatabaseFactory {
    override fun create() =
        ObjectDatabaseRepository(
            serverName,
            myProps,
            propRoot,
            repositoryObjectDatabaseGorgel,
            odbActorGorgel,
            messageDispatcherFactory,
            jsonToObjectDeserializer,
            getRequestFactory,
            putRequestFactory,
            updateRequestFactory,
            queryRequestFactory,
            removeRequestFactory,
            mustSendDebugReplies,
            connectionRetrierFactory,
            repositoryHostInitializer
        )
}
