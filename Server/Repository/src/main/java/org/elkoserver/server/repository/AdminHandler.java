package org.elkoserver.server.repository;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.util.trace.TraceFactory;

/**
 * Singleton handler for the repository 'admin' protocol.
 * <p>
 * The 'admin' protocol consists of these requests:
 * <p>
 * 'reinit' - Requests the repository to reinitialize itself.
 * <p>
 * 'shutdown' - Requests the repository to shut down, with an option to force
 * abrupt termination.
 */
class AdminHandler extends BasicProtocolHandler {
    /**
     * The repository for this handler.
     */
    private Repository myRepository;

    /**
     * Constructor.
     */
    AdminHandler(Repository repository, TraceFactory traceFactory) {
        super(traceFactory);
        myRepository = repository;
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
     * Handle the 'reinit' verb.
     * <p>
     * Request that the repository be reset.
     *
     * @param from The administrator sending the message.
     */
    @JSONMethod
    public void reinit(RepositoryActor from) throws MessageHandlerException {
        from.ensureAuthorizedAdmin();
        myRepository.reinit();
    }

    /**
     * Handle the 'shutdown' verb.
     * <p>
     * Request that the repository be shut down.
     *
     * @param from The administrator sending the message.
     * @param kill If true, shutdown immediately instead of cleaning up.
     */
    @JSONMethod({"kill"})
    public void shutdown(RepositoryActor from, OptBoolean kill)
            throws MessageHandlerException {
        from.ensureAuthorizedAdmin();
        myRepository.shutdown(kill.value(false));
    }
}
