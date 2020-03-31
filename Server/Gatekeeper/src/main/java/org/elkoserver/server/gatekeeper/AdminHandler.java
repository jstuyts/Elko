package org.elkoserver.server.gatekeeper;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;

/**
 * Singleton handler for the gatekeeper 'admin' protocol.
 *
 * The 'admin' protocol consists of these requests:
 *
 *   'director' - Requests that the director this gateway fronts for be
 *      changed or reported.
 *
 *   'reinit' - Requests the gatekeeper to reinitialize itself.
 *
 *   'shutdown' - Requests the gatekeeper to shut down, with an option to force
 *      abrupt termination.
 *
 */
class AdminHandler extends BasicProtocolHandler {
    /** The gatekeeper for this handler. */
    private Gatekeeper myGatekeeper;

    /**
     * Constructor.
     *
     * @param gatekeeper  The Gatekeeper object for this handler.
     */
    AdminHandler(Gatekeeper gatekeeper) {
        myGatekeeper = gatekeeper;
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'admin'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "admin";
    }

    /**
     * Handle the 'director' verb.
     *
     * Request that the Director be changed or reported.
     *
     * @param from  The administrator asking for the deletion.
     * @param hostport  Optional hostport for the new director.
     * @param auth  Optional authorization configuration for connection to the
     *    director.
     */
    @JSONMethod({ "hostport", "?auth" })
    public void director(GatekeeperActor from, OptString hostport,
                         AuthDesc auth)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();

        String hostportStr = hostport.value(null);
        if (hostportStr != null) {
            myGatekeeper.setDirectorHost(
                    new HostDesc("tcp", false, hostportStr,
                            AuthDesc.theOpenAuth, -1, false));
        }
        HostDesc directorHost = myGatekeeper.directorHost();
        from.send(msgDirector(this, directorHost.hostPort()));
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Request that the gatekeeper be reset.
     *
     * @param from  The administrator sending the message.
     */
    @JSONMethod
    public void reinit(GatekeeperActor from) throws MessageHandlerException {
        from.ensureAuthorizedAdmin();
        myGatekeeper.reinit();
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Request that the gatekeeper be shut down.
     *
     * @param from  The administrator sending the message.
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    @JSONMethod({ "kill" })
    public void shutdown(GatekeeperActor from, OptBoolean kill)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        myGatekeeper.shutdown(kill.value(false));
    }

    /**
     * Generate a 'director' message.
     */
    private static JSONLiteral msgDirector(Referenceable target, String hostport)
    {
        JSONLiteral msg = new JSONLiteral(target, "director");
        msg.addParameter("hostport", hostport);
        msg.finish();
        return msg;
    }
}
