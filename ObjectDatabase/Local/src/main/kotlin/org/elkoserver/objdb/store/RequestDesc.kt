package org.elkoserver.objdb.store

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory.type

/**
 * Description of a request for an object.
 *
 * @param myRefref  Reference string identifying the object requested.
 * @param myCollectionName  Name of collection to get from, or null to take
 *    the configured default.
 * @param myContents If true, retrieve the referenced object and any objects
 *    it contains; if false, only retrieve the referenced object itself.
 *
 * @see ObjectStore.getObjects ObjectStore.getObjects
 */
class RequestDesc(private val myRef: String, private val myCollectionName: String?, private val myContents: Boolean) : Encodable {

    /**
     * JSON-driven constructor.
     *
     * @param ref  Reference string identifying the object requested.
     * @param collectionName  Name of collection to get from, or null to take
     * the configured default.
     * @param contents If true, retrieve the referenced object and any objects
     * it contains; if false (the default if omitted), only retrieve the
     * referenced object itself.
     */
    @JSONMethod("ref", "coll", "contents")
    constructor(ref: String, collectionName: OptString, contents: OptBoolean)
            : this(ref, collectionName.value<String?>(null), contents.value(false)) {
    }

    /**
     * Get the name of the collection being queried, or null if it's to be the
     * default.
     *
     * @return the value of this request's collection name.
     */
    fun collectionName() = myCollectionName

    /**
     * Get the value of the contents flag.
     *
     * @return the value of this request's contents flag.
     */
    fun contents() = myContents

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            type("reqi", control).apply {
                addParameter("ref", myRef)
                if (myContents) {
                    addParameter("contents", myContents)
                }
                finish()
            }

    /**
     * Get the reference string of the requested object.
     *
     * @return the reference string of the requested object.
     */
    fun ref() = myRef
}
