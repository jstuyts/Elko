package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.util.trace.TraceFactory

/**
 * Singleton handler for the gatekeeper 'admin' protocol.
 *
 * The 'admin' protocol consists of these requests:
 *
 * 'director' - Requests that the director this gateway fronts for be
 * changed or reported.
 *
 * 'reinit' - Requests the gatekeeper to reinitialize itself.
 *
 * 'shutdown' - Requests the gatekeeper to shut down, with an option to force
 * abrupt termination.
 *
 * @param myGatekeeper  The Gatekeeper object for this handler.
 */
internal class AdminHandler(private val myGatekeeper: Gatekeeper, traceFactory: TraceFactory?) : BasicProtocolHandler(traceFactory) {

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'admin'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "admin"

    /**
     * Handle the 'director' verb.
     *
     * Request that the Director be changed or reported.
     *
     * @param from  The administrator asking for the deletion.
     * @param hostport  Optional hostport for the new director.
     * @param auth  Optional authorization configuration for connection to the
     * director.
     */
    @JSONMethod("hostport", "?auth")
    fun director(from: GatekeeperActor, hostport: OptString, auth: AuthDesc?) {
        from.ensureAuthorizedAdmin()
        val hostportStr = hostport.value<String?>(null)
        if (hostportStr != null) {
            myGatekeeper.setDirectorHost(HostDesc("tcp", false, hostportStr, AuthDesc.theOpenAuth, -1))
        }
        val directorHost = myGatekeeper.directorHost()
        from.send(msgDirector(this, directorHost!!.hostPort()!!))
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Request that the gatekeeper be reset.
     *
     * @param from  The administrator sending the message.
     */
    @JSONMethod
    fun reinit(from: GatekeeperActor) {
        from.ensureAuthorizedAdmin()
        myGatekeeper.reinit()
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Request that the gatekeeper be shut down.
     *
     * @param from  The administrator sending the message.
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    @JSONMethod("kill")
    fun shutdown(from: GatekeeperActor, kill: OptBoolean) {
        from.ensureAuthorizedAdmin()
        myGatekeeper.shutdown(kill.value(false))
    }

    companion object {
        /**
         * Generate a 'director' message.
         */
        private fun msgDirector(target: Referenceable, hostport: String) =
                JSONLiteralFactory.targetVerb(target, "director").apply {
                    addParameter("hostport", hostport)
                    finish()
                }
    }
}
