package org.elkoserver.server.context

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.concurrent.Executor

/**
 * MessageHandlerFactory class to associate new Users with new Connections.
 *
 * @param myContextor  The contextor for this server.
 * @param amAuthRequired  Flag indicating whether reservations are
 *    needed for entry.
 * @param myProtocol  Protocol these new connections will be speaking
 */
internal class UserActorFactory(
        private val myContextor: Contextor,
        private val runner: Executor,
        private val amAuthRequired: Boolean,
        private val myProtocol: String,
        private val userActorGorgel: Gorgel,
        private val userGorgelWithoutRef: Gorgel,
        private val timer: Timer,
        private val userActorCommGorgel: Gorgel,
        private val idGenerator: IdGenerator,
        private val mustSendDebugReplies: Boolean) : MessageHandlerFactory {

    /**
     * Produce a new user for a new connection.
     *
     * @param connection  The new connection.
     */
    override fun provideMessageHandler(connection: Connection) =
            UserActor(connection, myContextor, runner, amAuthRequired, myProtocol, userActorGorgel, userGorgelWithoutRef, timer, userActorCommGorgel, idGenerator, mustSendDebugReplies)

    override fun handleConnectionFailure() {
        // No action needed. This factory ignores failures.
    }
}
