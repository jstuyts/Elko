package org.elkoserver.foundation.json

import org.elkoserver.json.JsonLiteral

/**
 * Interface for an object that will deliver JSON messages somewhere.  Possible
 * implementors include immediate message targets, objects that pass messages
 * along network connections, and objects that represent groups of other
 * deliverers to which messages are fanned.
 */
interface Deliverer {
    /**
     * Send a message to this object.  The message may be anything that can be
     * represented in JSON; it is up to the sender and receiver to agree on
     * what it is and what it means.
     *
     * @param message  The message to send.
     */
    fun send(message: JsonLiteral)
}