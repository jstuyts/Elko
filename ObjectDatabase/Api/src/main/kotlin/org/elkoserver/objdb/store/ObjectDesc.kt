package org.elkoserver.objdb.store

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory.type

/**
 * Description of a requested object returned from the object store.
 *
 * @param myRef  Object reference of the object requested.
 * @param myObj Object description (a JSON string describing the object, if
 *    the object was retrieved, or null if retrieval failed).
 * @param myFailure  Error message string if retrieval failed, or null if
 *    retrieval succeeded.
 */
class ObjectDesc(private val myRef: String?, private val myObj: String?, private val myFailure: String?) : Encodable {

    /**
     * JSON-driven constructor.
     *
     * @param ref  Object reference of the object requested.
     * @param obj  Optional object description.
     * @param failure  Optional error message.
     */
    @JSONMethod("ref", "obj", "failure")
    constructor(ref: String?, obj: OptString, failure: OptString) : this(ref, obj.value<String?>(null), failure.value<String?>(null))

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
                addParameter("ref", myRef)
                addParameterOpt("obj", myObj)
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
     * Get the requested object's description.
     *
     * @return the requested object's description (a JSON string), or null if
     * there is no object (i.e., if this represents an error result).
     */
    fun obj() = myObj

    /**
     * Get the requested object's reference string.
     *
     * @return the object reference string of the requested object.
     */
    fun ref() = myRef
}
