package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.util.trace.TraceFactory

/**
 * Singleton handler for the gatekeeper 'user' protocol.
 *
 * The 'user' protocol consists of the requests:
 *
 * 'reserve' - Requests a reservation on the user's behalf for entry into a
 * particular context.
 *
 * 'setpassword' - Requests that the user's stored password be changed.
 *
 * @param myGatekeeper  The gatekeeper itself.
 */
internal class UserHandler(private val myGatekeeper: Gatekeeper, traceFactory: TraceFactory?) : BasicProtocolHandler(traceFactory) {

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
    @JSONMethod("protocol", "context", "id", "name", "password")
    fun reserve(from: GatekeeperActor, protocol: String, context: String, id: OptString, name: OptString, password: OptString) {
        val idStr = id.value<String?>(null)
        myGatekeeper.authorizer()!!.reserve(
                protocol, context, idStr, name.value<String?>(null), password.value<String?>(null),
                object : ReservationResultHandler {
                    override fun handleReservation( actor: String?, context: String?, name: String?, hostport: String?, auth: String?) {
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
    @JSONMethod("id", "oldpassword", "newpassword")
    fun setpassword(from: GatekeeperActor, id: String, oldpassword: OptString, newpassword: OptString) {
        myGatekeeper.authorizer()!!.setPassword(
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
        /**
         * Create a 'reserve' reply message.
         *
         * @param target  Object the message is being sent to.
         * @param id  The ID for which the reservation was requested, or null if
         * none.
         * @param context  Context the reservation is for.
         * @param actor  Actor the reservation is for, or null for anonymous.
         * @param hostPort  Host:port to connect to, or null in error case.
         * @param auth  Authorization code for entry, or null in error case.
         * @param deny  Error message in error case, or null in normal case.
         */
        private fun msgReserve(target: Referenceable, id: String?, context: String?, actor: String?, name: String?, hostPort: String?, auth: String?, deny: String?) =
                JSONLiteralFactory.targetVerb(target, "reserve").apply {
                    addParameterOpt("id", id)
                    addParameter("context", context)
                    addParameterOpt("actor", actor)
                    addParameterOpt("name", name)
                    addParameterOpt("hostport", hostPort)
                    addParameterOpt("auth", auth)
                    addParameterOpt("deny", deny)
                    finish()
                }

        /**
         * Create 'setpassword' reply message.
         *
         * @param target  Object the message is being sent to.
         * @param id  Actor whose password was requested to be changed.
         * @param failure  Error message, or null if no error.
         */
        private fun msgSetPassword(target: Referenceable, id: String, failure: String?) =
                JSONLiteralFactory.targetVerb(target, "setpassword").apply {
                    addParameter("id", id)
                    addParameterOpt("failure", failure)
                    finish()
                }
    }
}
