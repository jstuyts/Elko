package org.elkoserver.json

/**
 * Implementing this interface enables an object to be made referenceable in
 * JSON messages.
 */
interface Referenceable {
    /**
     * Obtain this object's reference string.
     *
     * @return a string referencing this object, suitable for addressing it in
     * a JSON message.
     */
    fun ref(): String
}
