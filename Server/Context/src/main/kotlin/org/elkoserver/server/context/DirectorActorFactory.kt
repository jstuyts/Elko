package org.elkoserver.server.context

import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel

class DirectorActorFactory(
        private val directorActorGorgel: Gorgel,
        private val reservationFactory: ReservationFactory,
        private val timer: Timer,
        private val mustSendDebugReplies: Boolean) {
    fun create(connection: Connection, dispatcher: MessageDispatcher, group: DirectorGroup, auth: AuthDesc): DirectorActor =
            DirectorActor(connection, dispatcher, group, auth, reservationFactory, timer, directorActorGorgel, mustSendDebugReplies)
}
