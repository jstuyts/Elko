package org.elkoserver.server.context

import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel

internal class PresencerGroupFactory(
        val server: Server,
        private val presencerGroupGorgel: Gorgel,
        private val presencerActorGorgel: Gorgel,
        private val messageDispatcher: MessageDispatcher,
        private val timer: Timer,
        private val props: ElkoProperties,
        private val mustSendDebugReplies: Boolean,
        private val connectionRetrierFactory: ConnectionRetrierFactory) {
    fun create(contextor: Contextor, presencers: List<HostDesc>) =
            PresencerGroup(
                    server,
                    contextor,
                    presencers,
                    presencerGroupGorgel,
                    messageDispatcher,
                    timer,
                    props,
                    presencerActorGorgel,
                    mustSendDebugReplies,
                    connectionRetrierFactory)
}
