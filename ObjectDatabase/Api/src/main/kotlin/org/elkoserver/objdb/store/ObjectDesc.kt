package org.elkoserver.objdb.store

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory.type

/**
 * Description of a requested object returned from the object store.
 *
 * @param ref  Object reference of the object requested.
 * @param obj Object description (a JSON string describing the object, if
 *    the object was retrieved, or null if retrieval failed).
 * @param failure  Error message string if retrieval failed, or null if
 *    retrieval succeeded.
 */
class ObjectDesc(internal val ref: String, val obj: String?, val failure: String?) : Encodable {

    /**
     * JSON-driven constructor.
     *
     * @param ref  Object reference of the object requested.
     * @param obj  Optional object description.
     * @param failure  Optional error message.
     */
    @JsonMethod("ref", "obj", "failure")
    constructor(ref: String, obj: OptString, failure: OptString) : this(ref, obj.value<String?>(null), failure.value<String?>(null))

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            type("obji", control).apply {
                addParameter("ref", ref)
                addParameterOpt("obj", obj)
                addParameterOpt("failure", failure)
                finish()
            }
}
