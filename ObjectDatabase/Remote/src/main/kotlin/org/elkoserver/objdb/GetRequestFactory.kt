package org.elkoserver.objdb

import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.json.EncodeControl.ForClientEncodeControl
import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import java.util.function.Consumer

class GetRequestFactory(private val tagGenerator: IdGenerator) {
    internal fun create(ref: String, collectionName: String?, handler: Consumer<Any?>?): PendingRequest {
        val tag = tagGenerator.generate().toString()
        return PendingRequest(handler, ref, collectionName, tag, msgGet(ref, tag, collectionName))
    }

    /**
     * Fill in this request's message field with a 'get' request.
     * @param ref  Reference string naming the object desired.
     * @param collectionName  Name of collection to get from, or null to take
     */
    private fun msgGet(ref: String, tag: String, collectionName: String?) =
            JsonLiteralFactory.targetVerb("rep", "get").apply {
                addParameter("tag", tag)
                val what = JsonLiteralFactory.type("reqi", ForClientEncodeControl).apply {
                    addParameter("ref", ref)
                    addParameter("contents", true)
                    addParameterOpt("coll", collectionName)
                    finish()
                }
                addParameter("what", JsonLiteralArray.singleElementArray(what))
                finish()
            }
}
