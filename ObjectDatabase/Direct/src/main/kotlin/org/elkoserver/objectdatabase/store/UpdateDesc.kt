package org.elkoserver.objectdatabase.store

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory.type

/**
 * Description of a request to update to the object store.
 *
 * @param ref  Object reference for the object.
 * @param version  Object version being updated
 * @param obj Object description (a JSON string describing the object).
 *
 * @see ObjectStore.updateObjects ObjectStore.updateObjects
 */
class UpdateDesc
@JsonMethod("ref", "version", "obj")
constructor(ref: String, val version: Int, obj: String)
    : PutDesc(ref, obj) {

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
            type("updatei", control).apply {
                addParameter("ref", ref)
                addParameter("version", version)
                addParameterOpt("obj", obj)
                finish()
            }
}
