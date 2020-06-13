package org.elkoserver.server.repository

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * MessageHandlerFactory class to create new actors for new connections to a
 * repository's listen port.
 *
 * @param myRepository  The repository this factory is making actors for.
 * @param myAuth  The authorization needed for connections to this port.
 * @param amAllowAdmin  If true, permit admin connections.
 * @param amAllowRep  If true, permit repository connections.
 */
internal class RepositoryActorFactory(
        internal val myRepository: Repository,
        private val myAuth: AuthDesc,
        internal val amAllowAdmin: Boolean,
        private val amAllowRep: Boolean,
        private val repositoryActorGorgel: Gorgel,
        private val traceFactory: TraceFactory,
        private val mustSendDebugReplies: Boolean) : MessageHandlerFactory {

    /**
     * Test whether repository connections are allowed.
     *
     * @return true if 'rep' connections are allowed.
     */
    fun allowRep(): Boolean = amAllowRep && !myRepository.isShuttingDown

    /**
     * Produce a new actor for a new connection.
     *
     * @param connection  The new connection.
     */
    override fun provideMessageHandler(connection: Connection?) =
            RepositoryActor(connection!!, this, repositoryActorGorgel, traceFactory, mustSendDebugReplies)

    /**
     * Return the object ref table for this factor.
     */
    fun refTable() = myRepository.myRefTable

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
