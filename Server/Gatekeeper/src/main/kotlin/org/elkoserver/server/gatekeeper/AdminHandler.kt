package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.util.trace.slf4j.Gorgel

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
internal class AdminHandler(
    private val myGatekeeper: Gatekeeper,
    private val shutdownWatcher: ShutdownWatcher,
    commGorgel: Gorgel
) : BasicProtocolHandler(commGorgel) {

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
    @JsonMethod("hostport", "?auth")
    fun director(from: GatekeeperActor, hostport: OptString, auth: AuthDesc?) {
        from.ensureAuthorizedAdmin()
        val hostportStr = hostport.valueOrNull()
        if (hostportStr != null) {
            myGatekeeper.setDirectorHost(HostDesc("tcp", false, hostportStr, AuthDesc.theOpenAuth, -1))
        }
        val directorHost = myGatekeeper.directorHost
        from.send(msgDirector(this, directorHost!!.hostPort!!))
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Request that the gatekeeper be reset.
     *
     * @param from  The administrator sending the message.
     */
    @JsonMethod
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
     */
    @JsonMethod
    fun shutdown(from: GatekeeperActor) {
        from.ensureAuthorizedAdmin()
        shutdownWatcher.noteShutdown()
    }
}
