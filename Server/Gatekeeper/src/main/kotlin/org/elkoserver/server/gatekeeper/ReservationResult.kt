package org.elkoserver.server.gatekeeper

/**
 * Descriptor object representing the results of a reservation request as
 * returned by a Director.
 */
class ReservationResult {
    private val myContextID: String
    private val myActor: String
    private val myHostport: String?
    private val myAuth: String?
    private val myDeny: String?

    /**
     * Construct a successful reservation result.
     *
     * @param contextID  The context ID.
     * @param actor  The actor.
     * @param hostport  Where to connect.
     * @param auth  Authorization code to use.
     */
    internal constructor(contextID: String, actor: String, hostport: String?, auth: String?) {
        myContextID = contextID
        myActor = actor
        myHostport = hostport
        myAuth = auth
        myDeny = null
    }

    /**
     * Construct a failed reservation result.
     *
     * @param contextID  The context ID.
     * @param actor  The actor.
     * @param deny  Why the reservation was denied.
     */
    internal constructor(contextID: String, actor: String, deny: String?) {
        myContextID = contextID
        myActor = actor
        myHostport = null
        myAuth = null
        myDeny = deny
    }

    /**
     * Get the actor ID for this result.
     *
     * @return the actor ID for this result.
     */
    fun actor() = myActor

    /**
     * Get the authorization code for this result.
     *
     * @return the authorization code for this result.
     */
    fun auth() = myAuth

    /**
     * Get the context ID for this result.
     *
     * @return the context ID string for this result.
     */
    fun contextID() = myContextID

    /**
     * Get the error message string for this result.
     *
     * @return the error message string for this result.
     */
    fun deny() = myDeny

    /**
     * Get the host:port string for this result.
     *
     * @return the host:port for this result.
     */
    fun hostport() = myHostport
}