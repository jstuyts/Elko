package org.elkoserver.objectdatabase.store

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory.type

/**
 * Description of a request write to the object store.
 *
 * @see ObjectStore.putObjects ObjectStore.putObjects
 *
 * @param ref  Object reference for the object.
 * @param obj Object description (a JSON string describing the object).
 */
open class PutDesc
@JsonMethod("ref", "obj")
constructor(val ref: String, val obj: String) : Encodable {
    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    @Suppress("SpellCheckingInspection")
    override fun encode(control: EncodeControl): JsonLiteral =
            type("puti", control).apply {
                addParameter("ref", ref)
                addParameterOpt("obj", obj)
                finish()
            }
}
