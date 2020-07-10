package org.elkoserver.foundation.server

import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.util.trace.slf4j.Gorgel

class BrokerActorFactory(
        private val dispatcher: MessageDispatcher,
        private val gorgel: Gorgel,
        private val mustSendDebugReplies: Boolean) {
    fun create(connection: Connection, server: Server, brokerHost: HostDesc) =
            BrokerActor(connection, dispatcher, server, brokerHost, gorgel, mustSendDebugReplies)
}
