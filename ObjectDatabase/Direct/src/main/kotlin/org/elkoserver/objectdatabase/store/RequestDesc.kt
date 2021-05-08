package org.elkoserver.objectdatabase.store

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory.type

/**
 * Description of a request for an object.
 *
 * @param ref  Reference string identifying the object requested.
 * @param contents If true, retrieve the referenced object and any objects
 *    it contains; if false, only retrieve the referenced object itself.
 *
 * @see ObjectStore.getObjects ObjectStore.getObjects
 */
class RequestDesc(val ref: String, private val contents: Boolean) : Encodable {

    /**
     * JSON-driven constructor.
     *
     * @param ref  Reference string identifying the object requested.
     * @param contents If true, retrieve the referenced object and any objects
     * it contains; if false (the default if omitted), only retrieve the
     * referenced object itself.
     */
    @JsonMethod("ref", "contents")
    constructor(ref: String, contents: OptBoolean)
            : this(ref, contents.value(false))

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
            type("reqi", control).apply {
                addParameter("ref", ref)
                if (contents) {
                    addParameter("contents", contents)
                }
                finish()
            }
}
