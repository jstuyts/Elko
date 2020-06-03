package org.elkoserver.objdb

import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory
import java.util.function.Consumer

class PutRequestFactory(private val tagGenerator: IdGenerator) {
    internal fun create(ref: String, obj: Encodable, collectionName: String?, requireNew: Boolean, handler: Consumer<Any?>?): PendingRequest {
        val tag = tagGenerator.generate().toString()
        return PendingRequest(handler, ref, collectionName, tag, msgPut(ref, tag, obj, collectionName, requireNew))
    }
    /**
     * Fill in this request's message field with a 'put' request.
     *
     * @param ref  Reference string naming the object to be put.
     * @param obj  The object itself.
     * @param collectionName  Name of collection to write, or null to take the
     * configured default (or the db doesn't use this abstraction).
     * @param requireNew  If true, require object 'ref' not already exist.
     */
    private fun msgPut(ref: String, tag: String, obj: Encodable, collectionName: String?, requireNew: Boolean) =
            JSONLiteralFactory.targetVerb("rep", "put").apply {
                addParameter("tag", tag)
                val what = JSONLiteralFactory.type("obji", EncodeControl.forClient).apply {
                    addParameter("ref", ref)
                    addParameter("obj", obj.encode(EncodeControl.forRepository)!!.sendableString())
                    addParameterOpt("coll", collectionName)
                    if (requireNew) {
                        addParameter("requirenew", requireNew)
                    }
                    finish()
                }
                addParameter("what", JSONLiteralArray.singleElementArray(what))
                finish()
            }
}
