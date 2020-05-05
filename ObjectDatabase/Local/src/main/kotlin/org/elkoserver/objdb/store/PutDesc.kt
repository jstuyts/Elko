package org.elkoserver.objdb.store

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory.type

/**
 * Description of a request write to the object store.
 *
 * @see ObjectStore.putObjects ObjectStore.putObjects
 *
 * @param myRef  Object reference for the object.
 * @param myObj Object description (a JSON string describing the object).
 * @param myCollectionName  Name of collection to write to, or null to take
 *    the configured default.
 * @param isRequireNew  If true and an object with the given ref already
 *     exists, the write fails.
 */
open class PutDesc(private val myRef: String, private val myObj: String, private val myCollectionName: String?, val isRequireNew: Boolean) : Encodable {
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
    @JSONMethod("ref", "obj", "coll", "requirenew")
    constructor(ref: String, obj: String, collectionName: OptString, requireNew: OptBoolean)
            : this(ref, obj, collectionName.value<String?>(null), requireNew.value(false))

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            type("puti", control).apply {
                addParameter("ref", myRef)
                addParameterOpt("obj", myObj)
                addParameterOpt("coll", myCollectionName)
                if (isRequireNew) {
                    addParameter("requirenew", isRequireNew)
                }
                finish()
            }

    /**
     * Get the collection name.
     *
     * @return the collection name to write to, or null to indicate the default
     */
    fun collectionName() = myCollectionName

    /**
     * Get the object's description.
     *
     * @return the object's description (a JSON string).
     */
    fun obj() = myObj

    /**
     * Get the object's reference string.
     *
     * @return the object reference string of the object to write.
     */
    fun ref() = myRef
}
