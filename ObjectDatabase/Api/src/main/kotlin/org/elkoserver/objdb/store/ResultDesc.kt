package org.elkoserver.objdb.store

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory.type

/**
 * Description of the result status of an object store operation.
 *
 * @param myRef  Object reference of the object that was the subject of the
 *    operation.
 * @param myFailure  Error message string, or null if the operation was
 *    successful.
 */
open class ResultDesc(private val myRef: String, private val myFailure: String?) : Encodable {

    /**
     * JSON-driven constructor.
     *
     * @param ref  Object reference of the object acted upon.
     * @param failure  Optional error message.
     */
    @JSONMethod("ref", "failure")
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
                addParameter("ref", myRef)
                addParameterOpt("failure", myFailure)
                finish()
            }

    /**
     * Get the error message string.
     *
     * @return the error message string, or null if there is none (i.e., if
     * this represents a success result).
     */
    fun failure() = myFailure

    /**
     * Get the subject object's reference string.
     *
     * @return the object reference string of the subject object.
     */
    fun ref() = myRef
}
