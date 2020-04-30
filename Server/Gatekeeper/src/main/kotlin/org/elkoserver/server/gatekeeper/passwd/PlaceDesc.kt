package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory

/**
 * Database object describing a place name to context mapping.
 *
 * @param myName  The place name.
 * @param myContextID  Identifier of the context that 'name' maps to.
 */
internal class PlaceDesc @JSONMethod("name", "context") constructor(private val myName: String, private val myContextID: String) : Encodable {

    /**
     * Obtain this place's context identifier.
     *
     * @return this place's context identifier string.
     */
    fun contextID() = myContextID

    /**
     * Encode this place for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("place", control).apply {
                addParameter("name", myName)
                addParameter("context", myContextID)
                finish()
            }

    /**
     * Obtain this place's name.
     *
     * @return this place's name string.
     */
    fun name() = myName
}
