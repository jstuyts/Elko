package org.elkoserver.server.director

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory

/**
 * MessageHandlerFactory class to create new actors for new connections to a
 * director's listen port.
 *
 * @param myDirector  The director itself.
 * @param myAuth  The authorization needed for connections to this port.
 * @param amAllowAdmin  If true, allow 'admin' connections.
 * @param amAllowProvider  If true, allow 'provider' connections.
 * @param amAllowUser  If true, allow 'user' connections.
 * @param tr  Trace object for diagnostics.
 */
internal class DirectorActorFactory(private val myDirector: Director, private val myAuth: AuthDesc, private val amAllowAdmin: Boolean,
                                    private val amAllowProvider: Boolean, private val amAllowUser: Boolean,
                                    private val tr: Trace, private val traceFactory: TraceFactory) : MessageHandlerFactory {

    /**
     * Test whether admin connections are allowed.
     *
     * @return true if 'admin' connections are allowed.
     */
    fun allowAdmin() = amAllowAdmin

    /**
     * Test whether provider connections are allowed.
     *
     * @return true if 'provider' connections are allowed.
     */
    fun allowProvider() = amAllowProvider && !myDirector.isShuttingDown

    /**
     * Test whether user connections are allowed.
     *
     * @return true if 'user' connections are allowed.
     */
    fun allowUser() = amAllowUser

    /**
     * Get this factory's director.
     *
     * @return the director object this factory uses.
     */
    fun director() = myDirector

    /**
     * Produce a new actor for a new connection.
     *
     * @param connection  The new connection.
     */
    override fun provideMessageHandler(connection: Connection) =
            DirectorActor(connection, this, tr, traceFactory)

    /**
     * Get this factory's ref table.
     *
     * @return the object ref table this factory uses.
     */
    fun refTable() = myDirector.refTable()

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
