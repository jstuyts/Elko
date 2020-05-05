package org.elkoserver.objdb.store

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory.type

/**
 * Description of the result status of an object update operation.
 *
 * @param ref  Object reference of the object that was the subject of the
 *    operation.
 * @param failure  Error message string, or null if the operation was
 *    successful.
 * @param isAtomicFailure  Flag that is true if operation would have
 *    completed but doing so would have violated atomicity.
 *
 * @see ObjectStore.updateObjects ObjectStore.updateObjects
 * @see RequestResultHandler
 */
class UpdateResultDesc(ref: String, failure: String?, val isAtomicFailure: Boolean) : ResultDesc(ref, failure) {
    /**
     * JSON-driven constructor.
     *
     * @param ref  Object reference of the object acted upon.
     * @param failure  Optional error message.
     */
    @JSONMethod("ref", "failure", "atomic")
    constructor(ref: String, failure: OptString, isAtomicFailure: Boolean) 
            : this(ref, failure.value<String?>(null), isAtomicFailure)

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            type("ustati", control).apply {
        addParameter("ref", ref())
        addParameterOpt("failure", failure())
        addParameter("atomic", isAtomicFailure)
        finish()
    }
}
