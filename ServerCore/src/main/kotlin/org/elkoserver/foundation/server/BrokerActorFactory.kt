package org.elkoserver.foundation.server

import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.slf4j.Gorgel

class BrokerActorFactory(
        private val dispatcher: MessageDispatcher,
        private val serverLoadMonitor: ServerLoadMonitor,
        private val gorgel: Gorgel,
        private val mustSendDebugReplies: Boolean,
        private val shutdownWatcher: ShutdownWatcher) {
    fun create(connection: Connection, server: Server, auth: AuthDesc): BrokerActor =
            BrokerActor(connection, dispatcher, server, serverLoadMonitor, shutdownWatcher, auth, gorgel, mustSendDebugReplies)
}
