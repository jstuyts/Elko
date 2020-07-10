package org.elkoserver.server.workshop.bank

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral

/**
 * Object representing a defined currency: a type token denominating a
 * monetary value.
 *
 * @param name  The currency name.
 * @param memo  Annotation on currency.
 */
class Currency @JsonMethod("name", "memo") internal constructor(internal val name: String, internal val memo: String) : Encodable {

    /**
     * Encode this currency descriptor for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this currency.
     */
    override fun encode(control: EncodeControl) =
            JsonLiteral(control).apply {
                addParameter("name", name)
                addParameter("memo", memo)
                finish()
            }
}
