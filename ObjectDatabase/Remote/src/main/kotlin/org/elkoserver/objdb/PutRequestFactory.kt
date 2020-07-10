package org.elkoserver.objdb

import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl.ForClientEncodeControl
import org.elkoserver.json.EncodeControl.ForRepositoryEncodeControl
import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
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
            JsonLiteralFactory.targetVerb("rep", "put").apply {
                addParameter("tag", tag)
                val what = JsonLiteralFactory.type("obji", ForClientEncodeControl).apply {
                    addParameter("ref", ref)
                    addParameter("obj", obj.encode(ForRepositoryEncodeControl)!!.sendableString())
                    addParameterOpt("coll", collectionName)
                    if (requireNew) {
                        addParameter("requirenew", requireNew)
                    }
                    finish()
                }
                addParameter("what", JsonLiteralArray.singleElementArray(what))
                finish()
            }
}
