package org.elkoserver.objectdatabase.store

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory.type

/**
 * Description of a request to update to the object store.
 *
 * @param ref  Object reference for the object.
 * @param version  Object version being updated
 * @param obj Object description (a JSON string describing the object).
 * @param collectionName  Name of collection to write to, or null to take
 *    the configured default.
 *
 * @see ObjectStore.updateObjects ObjectStore.updateObjects
 */
class UpdateDesc(ref: String, val version: Int, obj: String, collectionName: String?)
    : PutDesc(ref, obj, collectionName, false) {

    /**
     * JSON-driven constructor.
     *
     * @param ref  Object reference of the object to write.
     * @param version  Object version being updated
     * @param obj  Object description.
     * @param collectionName  Name of collection to write to, or null to take
     * the configured default.
     */
    @JsonMethod("ref", "version", "obj", "coll")
    constructor(ref: String, version: Int, obj: String, collectionName: OptString)
            : this(ref, version, obj, collectionName.valueOrNull())

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl): JsonLiteral =
            type("updatei", control).apply {
                addParameter("ref", ref)
                addParameter("version", version)
                addParameterOpt("obj", obj)
                addParameterOpt("coll", collectionName)
                finish()
            }
}
