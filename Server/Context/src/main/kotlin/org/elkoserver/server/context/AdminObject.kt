package org.elkoserver.server.context

import org.elkoserver.foundation.actor.BasicProtocolHandler

/**
 * Abstract base class for internal objects that want to be addressable in
 * messages delivered over internal (i.e., behind the firewall) communications
 * connections to the server.
 */
abstract class AdminObject : BasicProtocolHandler(), InternalObject {
    /** The contextor for this server.  */
    private var myContextor: Contextor? = null

    /** The name by which this object will be addressed in messages.  */
    private var myRef: String? = null

    /**
     * Make this object live inside the context server.
     *
     * @param ref  Reference string identifying this object in the static
     * object table.
     * @param contextor  The contextor for this server.
     */
    override fun activate(ref: String, contextor: Contextor) {
        myRef = ref
        myContextor = contextor
    }

    /**
     * Obtain the contextor for this server.
     *
     * @return the Contextor object for this server.
     */
    override fun contextor() = myContextor

    /* ----- Referenceable interface --------------------------------------- */
    /**
     * Obtain this object's reference string.
     *
     * @return a string that can be used to refer to this object in JSON
     * messages, either as the message target or as a parameter value.
     */
    override fun ref() = myRef
}
