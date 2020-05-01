package org.elkoserver.server.context

import org.elkoserver.json.Referenceable

/**
 * Interface implemented by static objects that wish to have access to the
 * internal state of the server, and rather than being self-contained entities.
 */
internal interface InternalObject : Referenceable {
    /**
     * Make this object live inside the context server.
     *
     * @param ref  Reference string identifying this object in the static
     * object table.
     * @param contextor  The contextor for this server.
     */
    fun activate(ref: String?, contextor: Contextor?)

    /**
     * Obtain the contextor for this server.
     *
     * @return the Contextor object for this server.
     */
    fun contextor(): Contextor?
}