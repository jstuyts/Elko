package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * MessageHandlerFactory class to create new actors for new connections to a
 * gatekeeper's listen port.
 *
 * @param myGatekeeper  The gatekeeper itself.
 * @param myAuth  The authorization needed for connections to this port.
 * @param amAllowAdmin  If true, allow 'admin' connections.
 */
internal class GatekeeperActorFactory(private val myGatekeeper: Gatekeeper, private val myAuth: AuthDesc,
                                      private val amAllowAdmin: Boolean, private val amAllowUser: Boolean,
                                      private val myActionTimeout: Int,
                                      private val gatekeeperActorGorgel: Gorgel,
                                      private val timer: Timer, private val traceFactory: TraceFactory) : MessageHandlerFactory {

    /**
     * Test whether admin connections are allowed.
     *
     * @return true if 'admin' connections are allowed.
     */
    fun allowAdmin() = amAllowAdmin

    /**
     * Test whether user connections are allowed.
     *
     * @return true if 'user' connections are allowed.
     */
    fun allowUser() = amAllowUser

    /**
     * Get this factory's gatekeeper.
     *
     * @return the gatekeeper object this factory uses.
     */
    fun gatekeeper() = myGatekeeper

    /**
     * Produce a new user for a new connection.
     *
     * @param connection  The new connection.
     */
    override fun provideMessageHandler(connection: Connection?) =
            GatekeeperActor(connection!!, this, myActionTimeout, gatekeeperActorGorgel, timer, traceFactory)

    /**
     * Get this factory's ref table.
     *
     * @return the object ref table this factory uses.
     */
    fun refTable() = myGatekeeper.refTable()

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
