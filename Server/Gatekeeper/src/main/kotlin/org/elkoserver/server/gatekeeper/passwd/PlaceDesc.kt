package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory

/**
 * Database object describing a place name to context mapping.
 *
 * @param name  The place name.
 * @param contextID  Identifier of the context that 'name' maps to.
 */
internal class PlaceDesc @JsonMethod("name", "context") constructor(private val name: String, internal val contextID: String) : Encodable {

    /**
     * Encode this place for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            JsonLiteralFactory.type("place", control).apply {
                addParameter("name", name)
                addParameter("context", contextID)
                finish()
            }
}
