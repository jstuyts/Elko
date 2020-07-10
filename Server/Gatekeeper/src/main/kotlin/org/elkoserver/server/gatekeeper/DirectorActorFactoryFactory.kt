package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.util.trace.slf4j.Gorgel

internal class DirectorActorFactoryFactory(
        private val directorActorFactoryGorgel: Gorgel,
        private val directorActorGorgel: Gorgel,
        private val messageDispatcher: MessageDispatcher,
        private val mustSendDebugReplies: Boolean,
        private val connectionRetrierFactory: ConnectionRetrierFactory) {
    fun create(gatekeeper: Gatekeeper) =
            DirectorActorFactory(
                    gatekeeper,
                    directorActorFactoryGorgel,
                    directorActorGorgel,
                    messageDispatcher,
                    mustSendDebugReplies,
                    connectionRetrierFactory)
}