package org.elkoserver.server.director

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.ordinalgeneration.OrdinalGenerator
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * MessageHandlerFactory class to create new actors for new connections to a
 * director's listen port.
 *
 * @param director  The director itself.
 * @param myAuth  The authorization needed for connections to this port.
 * @param allowAdmin  If true, allow 'admin' connections.
 * @param amAllowProvider  If true, allow 'provider' connections.
 * @param allowUser  If true, allow 'user' connections.
 */
internal class DirectorActorFactory(
        internal val director: Director,
        private val myAuth: AuthDesc,
        internal val allowAdmin: Boolean,
        private val amAllowProvider: Boolean,
        internal val allowUser: Boolean,
        private val directorActorGorgel: Gorgel,
        private val providerGorgel: Gorgel,
        private val directorActorCommGorgel: Gorgel,
        private val ordinalGenerator: OrdinalGenerator,
        private val mustSendDebugReplies: Boolean) : MessageHandlerFactory {

    /**
     * Test whether provider connections are allowed.
     *
     * @return true if 'provider' connections are allowed.
     */
    fun allowProvider() = amAllowProvider && !director.isShuttingDown

    /**
     * Produce a new actor for a new connection.
     *
     * @param connection  The new connection.
     */
    override fun provideMessageHandler(connection: Connection?) =
            DirectorActor(connection!!, this, directorActorGorgel, providerGorgel, directorActorCommGorgel, ordinalGenerator, mustSendDebugReplies)

    /**
     * Get this factory's ref table.
     *
     * @return the object ref table this factory uses.
     */
    fun refTable() = director.refTable

    /**
     * Check the actor's authorization.
     *
     * @param auth  Authorization being used.
     *
     * @return true if 'auth' correctly authorizes connection under
     * this factory's authorization configuration.
     */
    fun verifyAuthorization(auth: AuthDesc?) = myAuth.verify(auth)
}
