package org.elkoserver.server.gatekeeper

/**
 * Interface for an [Authorizer] object to deliver the results of
 * servicing a client's set password request.
 */
interface SetPasswordResultHandler {
    /**
     * Deliver the results of processing a set password request.
     *
     * @param failure  Error message in case of failure or null in case of
     * success.
     */
    fun handle(failure: String?)
}
