package org.elkoserver.server.presence

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * MessageHandlerFactory class to create actors for new connections to a
 * presence server's listen port.
 *
 * @param myPresenceServer  The presence server itself.
 * @param myAuth  The authorization needed for connections to this port.
 * @param amAllowAdmin  If true, allow 'admin' connections.
 * @param amAllowClient  If true, allow 'client' connections.
 */
internal class PresenceActorFactory(internal val myPresenceServer: PresenceServer, private val myAuth: AuthDesc,
                                    internal val amAllowAdmin: Boolean, private val amAllowClient: Boolean,
                                    private val presenceActorGorgel: Gorgel,
                                    private val traceFactory: TraceFactory) : MessageHandlerFactory {

    /**
     * Test whether client connections are allowed.
     *
     * @return true if 'client' connections are allowed.
     */
    fun allowClient() = amAllowClient && !myPresenceServer.isShuttingDown

    /**
     * Produce a new actor for a new connection.
     *
     * @param connection  The new connection.
     */
    override fun provideMessageHandler(connection: Connection?) =
            PresenceActor(connection!!, this, presenceActorGorgel, traceFactory)

    /**
     * Get this factory's ref table.
     *
     * @return the object ref table this factory uses.
     */
    fun refTable() = myPresenceServer.refTable

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
