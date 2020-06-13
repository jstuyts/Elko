package org.elkoserver.server.broker

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.ordinalgeneration.LongOrdinalGenerator
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * MessageHandlerFactory class to create actors for new connections to a
 * broker's listen port.
 *
 * @param broker  The broker itself.
 * @param myAuth  The authorization needed for connections to this port.
 * @param allowAdmin  If true, allow 'admin' connections.
 * @param allowClient  If true, allow 'client' connections.
 */
internal class BrokerActorFactory(
        internal val broker: Broker,
        private val myAuth: AuthDesc,
        internal val allowAdmin: Boolean,
        internal val allowClient: Boolean,
        private val brokerActorGorgel: Gorgel,
        private val traceFactory: TraceFactory,
        private val clientOrdinalGenerator: LongOrdinalGenerator,
        private val mustSendDebugReplies: Boolean) : MessageHandlerFactory {

    /**
     * Produce a new actor for a new connection.
     *
     * @param connection The new connection.
     */
    override fun provideMessageHandler(connection: Connection?) =
            BrokerActor(connection!!, this, brokerActorGorgel, traceFactory, clientOrdinalGenerator, mustSendDebugReplies)

    /**
     * Get this factory's ref table.
     *
     * @return the object ref table this factory uses.
     */
    fun refTable() = broker.refTable

    /**
     * Check the actor's authorization.
     *
     * @param auth Authorization being used.
     * @return true if 'auth' correctly authorizes connection under
     * this factory's authorization configuration.
     */
    fun verifyAuthorization(auth: AuthDesc?) = myAuth.verify(auth)
}
