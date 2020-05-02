package org.elkoserver.json

/**
 * Implementing this interface enables an object's state to be output as a JSON
 * object literal.
 */
interface Encodable {
    /**
     * Produce a [JSONLiteral] representing the encoded state of this
     * object, suitable for transmission over a messaging medium or for writing
     * to persistent storage.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a [JSONLiteral] representing the encoded state of this
     * object.
     */
    fun encode(control: EncodeControl): JSONLiteral?
}
