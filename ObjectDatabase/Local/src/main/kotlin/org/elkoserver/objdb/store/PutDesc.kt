package org.elkoserver.objdb.store

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
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
 * @param collectionName  Name of collection to write to, or null to take
 *    the configured default.
 * @param isRequireNew  If true and an object with the given ref already
 *     exists, the write fails.
 */
open class PutDesc(val ref: String, val obj: String, val collectionName: String?, val isRequireNew: Boolean) : Encodable {
    /**
     * Test if this write must be to a new object.
     *
     * @return true if this write must be to a new object.
     */

    /**
     * JSON-driven constructor.
     *
     * @param ref  Object reference of the object to write.
     * @param obj  Object description.
     * @param collectionName  Name of collection to write to, or omit to take
     * the configured default.
     * @param requireNew  Optional flag to force failure if object with ref
     * already exists.
     */
    @JsonMethod("ref", "obj", "coll", "requirenew")
    constructor(ref: String, obj: String, collectionName: OptString, requireNew: OptBoolean)
            : this(ref, obj, collectionName.valueOrNull(), requireNew.value(false))

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl): JsonLiteral =
            type("puti", control).apply {
                addParameter("ref", ref)
                addParameterOpt("obj", obj)
                addParameterOpt("coll", collectionName)
                if (isRequireNew) {
                    addParameter("requirenew", isRequireNew)
                }
                finish()
            }
}
