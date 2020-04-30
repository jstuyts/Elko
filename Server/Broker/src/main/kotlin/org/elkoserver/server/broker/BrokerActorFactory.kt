package org.elkoserver.server.broker

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory

/**
 * MessageHandlerFactory class to create actors for new connections to a
 * broker's listen port.
 *
 * @param myBroker  The broker itself.
 * @param myAuth  The authorization needed for connections to this port.
 * @param amAllowAdmin  If true, allow 'admin' connections.
 * @param amAllowClient  If true, allow 'client' connections.
 * @param tr  Trace object for diagnostics.
 */
internal class BrokerActorFactory(private val myBroker: Broker, private val myAuth: AuthDesc, private val amAllowAdmin: Boolean,
                                  private val amAllowClient: Boolean, private val tr: Trace, private val traceFactory: TraceFactory) : MessageHandlerFactory {

    /**
     * Test whether admin connections are allowed.
     *
     * @return true if 'admin' connections are allowed.
     */
    fun allowAdmin() = amAllowAdmin

    /**
     * Test whether client connections are allowed.
     *
     * @return true if 'client' connections are allowed.
     */
    fun allowClient() = amAllowClient

    /**
     * Get this factory's broker.
     *
     * @return the broker object this factory uses.
     */
    fun broker() = myBroker

    /**
     * Produce a new actor for a new connection.
     *
     * @param connection The new connection.
     */
    override fun provideMessageHandler(connection: Connection) =
            BrokerActor(connection, this, tr, traceFactory)

    /**
     * Get this factory's ref table.
     *
     * @return the object ref table this factory uses.
     */
    fun refTable() = myBroker.refTable()

    /**
     * Check the actor's authorization.
     *
     * @param auth Authorization being used.
     * @return true if 'auth' correctly authorizes connection under
     * this factory's authorization configuration.
     */
    fun verifyAuthorization(auth: AuthDesc?) = myAuth.verify(auth)
}
