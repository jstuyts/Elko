package org.elkoserver.foundation.server.metadata

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory

/**
 * Description of the load on a server.
 */
class LoadDesc @JsonMethod("label", "load", "provider") constructor(private val label: String, private val load: Double, private val providerID: Int) : Encodable {

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            JsonLiteralFactory.type("loaddesc", control).apply {
                addParameter("label", label)
                addParameter("load", load)
                addParameter("provider", providerID)
                finish()
            }

    /**
     * Encode this descriptor as a single-element JSONLiteralArray.
     */
    fun encodeAsArray() =
            JsonLiteralArray().apply {
                addElement(this)
                finish()
            }
}
