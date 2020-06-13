package org.elkoserver.server.context

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * MessageHandlerFactory class to create new actors for new connections to a
 * context server's internal listen port.
 *
 * @param contextor  The contextor itself.
 * @param myAuth  The authorization needed for connections to this port.
 */
internal class InternalActorFactory(
        internal val contextor: Contextor,
        private val myAuth: AuthDesc,
        private val internalActorGorgel: Gorgel,
        private val traceFactory: TraceFactory,
        private val mustSendDebugReplies: Boolean) : MessageHandlerFactory {

    /**
     * Produce a new actor for a new connection.
     *
     * @param connection  The new connection.
     */
    override fun provideMessageHandler(connection: Connection?) =
            InternalActor(connection!!, this, internalActorGorgel, traceFactory, mustSendDebugReplies)

    /**
     * Check the actor's authorization.
     *
     * @param auth  Authorization being used.
     *
     * @return true if 'auth' correctly authorizes connection under
     * this factory's authorization configuration.
     */
    fun verifyInternalAuthorization(auth: AuthDesc?) = myAuth.verify(auth)
}
