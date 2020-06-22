package org.elkoserver.server.workshop

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * MessageHandlerFactory class to create new actors for new connections to a
 * workshop's listen port.
 *
 *
 * @param workshop  The workshop this factory is making actors for.
 * @param myAuth  The authorization needed for connections to this port.
 * @param allowAdmin  If true, permit admin connections.
 * @param amAllowClient  If true, permit workshop client connections.
 */
internal class WorkshopActorFactory(
        internal val workshop: Workshop,
        private val myAuth: AuthDesc,
        internal val allowAdmin: Boolean,
        private val amAllowClient: Boolean,
        private val workshopActorGorgel: Gorgel,
        private val workshopActorCommGorgel: Gorgel,
        private val mustSendDebugReplies: Boolean) : MessageHandlerFactory {

    /**
     * Test whether workshop client connections are allowed.
     *
     * @return true if workshop client connections are allowed.
     */
    fun allowClient() = amAllowClient && !workshop.isShuttingDown

    /**
     * Produce a new actor for a new connection.
     *
     * @param connection  The new connection.
     */
    override fun provideMessageHandler(connection: Connection?) =
            WorkshopActor(connection!!, this, workshopActorGorgel, workshopActorCommGorgel, mustSendDebugReplies)

    /**
     * Check an actor's authorization.
     *
     * @param auth  Authorization being used.
     *
     * @return true if 'auth' correctly authorizes connection under
     * this factory's authorization configuration.
     */
    fun verifyAuthorization(auth: AuthDesc?) = myAuth.verify(auth)
}
