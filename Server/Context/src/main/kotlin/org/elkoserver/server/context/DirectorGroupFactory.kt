package org.elkoserver.server.context

import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel

internal class DirectorGroupFactory(
        val server: Server,
        private val directorGroupGorgel: Gorgel,
        private val reservationFactory: ReservationFactory,
        private val directorActorFactory: DirectorActorFactory,
        private val messageDispatcher: MessageDispatcher,
        private val timer: Timer,
        private val props: ElkoProperties,
        private val connectionRetrierFactory: ConnectionRetrierFactory) {
    fun create(contextor: Contextor, directors: List<HostDesc>, listeners: List<HostDesc>) =
            DirectorGroup(
                    server,
                    contextor,
                    directors,
                    listeners,
                    directorGroupGorgel,
                    messageDispatcher,
                    reservationFactory,
                    directorActorFactory,
                    timer,
                    props,
                    connectionRetrierFactory)
}
