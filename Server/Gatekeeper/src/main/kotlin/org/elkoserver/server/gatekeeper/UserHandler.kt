package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Singleton handler for the gatekeeper 'user' protocol.
 *
 * The 'user' protocol consists of the requests:
 *
 * 'reserve' - Requests a reservation on the user's behalf for entry into a
 * particular context.
 *
 * 'setpassword' - Requests that the user's stored password be changed.
 */
internal class UserHandler(private val myAuthorizer: Authorizer, commGorgel: Gorgel) : BasicProtocolHandler(commGorgel) {

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'gatekeeper'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "gatekeeper"

    /**
     * Handle the 'reserve' verb.
     *
     * Request a reservation to enter a context server.
     *
     * @param from  The user asking for the reservation.
     * @param protocol  The protocol it wants to use.
     * @param context  The context it is seeking.
     * @param id  The user who is asking for this.
     * @param name  Optional readable name for the user.
     * @param password  Password for entry, when relevant.
     */
    @JsonMethod("protocol", "context", "id", "name", "password")
    fun reserve(from: GatekeeperActor, protocol: String, context: String, id: OptString, name: OptString, password: OptString) {
        val idStr = id.value<String?>(null)
        myAuthorizer.reserve(
                protocol, context, idStr, name.value<String?>(null), password.value<String?>(null),
                object : ReservationResultHandler {
                    override fun handleReservation(actor: String?, context: String?, name: String?, hostport: String?, auth: String?) {
                        from.send(msgReserve(this@UserHandler, idStr, context, actor, name, hostport, auth, null))
                    }

                    override fun handleFailure(failure: String?) {
                        from.send(msgReserve(this@UserHandler, idStr, context, null, null, null, null, failure))
                    }
                })
    }

    /**
     * Handle the 'setpassword' verb.
     *
     * Request to change a user's password.
     *
     * @param from  The connection asking for the password change.
     * @param id  The user who is asking for this.
     * @param oldpassword  Current password, to check for permission.
     * @param newpassword  New password setting.
     */
    @JsonMethod("id", "oldpassword", "newpassword")
    fun setpassword(from: GatekeeperActor, id: String, oldpassword: OptString, newpassword: OptString) {
        myAuthorizer.setPassword(
                id,
                oldpassword.value<String?>(null),
                newpassword.value<String?>(null),
                object : SetPasswordResultHandler {
                    override fun handle(failure: String?) {
                        from.send(msgSetPassword(this@UserHandler, id, failure))
                    }
                })
    }

    companion object {

    }
}
