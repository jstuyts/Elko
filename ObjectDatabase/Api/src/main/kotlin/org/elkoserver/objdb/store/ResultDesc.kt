package org.elkoserver.objdb.store

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory.type

/**
 * Description of the result status of an object store operation.
 *
 * @param ref  Object reference of the object that was the subject of the
 *    operation.
 * @param failure  Error message string, or null if the operation was
 *    successful.
 */
open class ResultDesc(protected val ref: String, val failure: String?) : Encodable {

    /**
     * JSON-driven constructor.
     *
     * @param ref  Object reference of the object acted upon.
     * @param failure  Optional error message.
     */
    @JsonMethod("ref", "failure")
    constructor(ref: String, failure: OptString) : this(ref, failure.value<String?>(null))

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            type("stati", control).apply {
                addParameter("ref", ref)
                addParameterOpt("failure", failure)
                finish()
            }
}
