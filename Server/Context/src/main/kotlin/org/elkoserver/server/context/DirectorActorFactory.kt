package org.elkoserver.server.context

import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel

class DirectorActorFactory(
        private val directorActorGorgel: Gorgel,
        private val reservationFactory: ReservationFactory,
        private val timer: Timer,
        private val mustSendDebugReplies: Boolean) {
    fun create(connection: Connection, dispatcher: MessageDispatcher, group: DirectorGroup, host: HostDesc) =
            DirectorActor(connection, dispatcher, group, host, reservationFactory, timer, directorActorGorgel, mustSendDebugReplies)
}
