package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * MessageHandlerFactory class to create new actors for new connections to a
 * gatekeeper's listen port.
 *
 * @param gatekeeper  The gatekeeper itself.
 * @param myAuth  The authorization needed for connections to this port.
 * @param allowAdmin  If true, allow 'admin' connections.
 */
internal class GatekeeperActorFactory(
        private val gatekeeper: Gatekeeper,
        private val myAuth: AuthDesc,
        internal val allowAdmin: Boolean,
        private val allowUser: Boolean,
        private val myActionTimeout: Int,
        private val gatekeeperActorGorgel: Gorgel,
        private val timer: Timer,
        private val gatekeeperActorCommGorgel: Gorgel,
        private val mustSendDebugReplies: Boolean) : MessageHandlerFactory {

    /**
     * Produce a new user for a new connection.
     *
     * @param connection  The new connection.
     */
    override fun provideMessageHandler(connection: Connection) =
            GatekeeperActor(connection, this, myActionTimeout, gatekeeperActorGorgel, timer, gatekeeperActorCommGorgel, mustSendDebugReplies)

    override fun handleConnectionFailure() {
        // No action needed. This factory ignores failures.
    }

    /**
     * Get this factory's ref table.
     *
     * @return the object ref table this factory uses.
     */
    fun refTable() = gatekeeper.refTable

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
