package org.elkoserver.server.repository

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Singleton handler for the repository 'admin' protocol.
 *
 * The 'admin' protocol consists of these requests:
 *
 * 'reinit' - Requests the repository to reinitialize itself.
 *
 * 'shutdown' - Requests the repository to shut down, with an option to force
 * abrupt termination.
 */
internal class AdminHandler(private val myRepository: Repository, commGorgel: Gorgel) : BasicProtocolHandler(commGorgel) {

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'admin'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "admin"

    /**
     * Handle the 'reinit' verb.
     *
     * Request that the repository be reset.
     *
     * @param from The administrator sending the message.
     */
    @JSONMethod
    fun reinit(from: RepositoryActor) {
        from.ensureAuthorizedAdmin()
        myRepository.reinit()
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Request that the repository be shut down.
     *
     * @param from The administrator sending the message.
     */
    @JSONMethod
    fun shutdown(from: RepositoryActor) {
        from.ensureAuthorizedAdmin()
        myRepository.shutdown()
    }
}
