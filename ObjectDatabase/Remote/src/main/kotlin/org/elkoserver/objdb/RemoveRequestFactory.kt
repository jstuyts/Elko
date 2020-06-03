package org.elkoserver.objdb

import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory
import java.util.function.Consumer

class RemoveRequestFactory(private val tagGenerator: IdGenerator) {
    internal fun create(ref: String, collectionName: String?, handler: Consumer<Any?>?): PendingRequest {
        val tag = tagGenerator.generate().toString()
        return PendingRequest(handler, ref, collectionName, tag, msgRemove(ref, tag, collectionName))
    }

    /**
     * Fill in this request's message field with a 'remove' request.
     *
     * @param ref  Reference string naming the object to remove.
     * @param collectionName  Name of collection to remove from, or null to
     * take the configured default (or the db doesn't use this abstraction).
     */
    private fun msgRemove(ref: String, tag: String, collectionName: String?) =
            JSONLiteralFactory.targetVerb("rep", "remove").apply {
                addParameter("tag", tag)
                val what = JSONLiteralFactory.type("reqi", EncodeControl.forClient).apply {
                    addParameter("ref", ref)
                    addParameterOpt("coll", collectionName)
                    finish()
                }
                addParameter("what", JSONLiteralArray.singleElementArray(what))
                finish()
            }
}
