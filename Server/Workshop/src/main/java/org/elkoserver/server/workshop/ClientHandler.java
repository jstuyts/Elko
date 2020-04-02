package org.elkoserver.server.workshop;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.util.trace.TraceFactory;

/**
 * Singleton handler for the workshop service protocol.
 *
 * The workshop service protocol currently has no requests of its own defined.
 * However, client control messages for the workshop in principle will go here.
 */
class ClientHandler extends BasicProtocolHandler {
    /** The workshop server proper. */
    private Workshop myWorkshop;

    /**
     * Constructor.
     */
    ClientHandler(Workshop workshop, TraceFactory traceFactory) {
        super(traceFactory);
        myWorkshop = workshop;
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'workshop'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "workshop";
    }
}
