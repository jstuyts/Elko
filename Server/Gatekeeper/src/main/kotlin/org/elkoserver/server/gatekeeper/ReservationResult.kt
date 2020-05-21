package org.elkoserver.server.gatekeeper

/**
 * Descriptor object representing the results of a reservation request as
 * returned by a Director.
 */
class ReservationResult {
    internal val contextID: String
    internal val actor: String
    internal val hostport: String?
    internal val auth: String?
    internal val deny: String?

    /**
     * Construct a successful reservation result.
     *
     * @param theContextID  The context ID.
     * @param theActor  The actor.
     * @param theHostport  Where to connect.
     * @param theAuth  Authorization code to use.
     */
    internal constructor(theContextID: String, theActor: String, theHostport: String?, theAuth: String?) {
        contextID = theContextID
        actor = theActor
        hostport = theHostport
        auth = theAuth
        deny = null
    }

    /**
     * Construct a failed reservation result.
     *
     * @param theContextID  The context ID.
     * @param theActor  The actor.
     * @param theDeny  Why the reservation was denied.
     */
    internal constructor(theContextID: String, theActor: String, theDeny: String?) {
        contextID = theContextID
        actor = theActor
        hostport = null
        auth = null
        deny = theDeny
    }
}