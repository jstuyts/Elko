package org.elkoserver.objdb

import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl.ForClientEncodeControl
import org.elkoserver.json.EncodeControl.ForRepositoryEncodeControl
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory
import java.util.function.Consumer

class UpdateRequestFactory(private val tagGenerator: IdGenerator) {
    internal fun create(ref: String, version: Int, obj: Encodable, collectionName: String?, handler: Consumer<Any?>?): PendingRequest {
        val tag = tagGenerator.generate().toString()
        return PendingRequest(handler, ref, collectionName, tag, msgUpdate(ref, tag, version, obj, collectionName))
    }

    /**
     * Fill in this request's message field with an 'update' request.
     *
     * @param ref  Reference string naming the object to be put.
     * @param version  Version number of the version of the object to update.
     * @param obj  The object itself.
     * @param collectionName  Name of collection to write, or null to take the
     * configured default (or the db doesn't use this abstraction).
     */
    private fun msgUpdate(ref: String, tag: String, version: Int, obj: Encodable, collectionName: String?) =
            JSONLiteralFactory.targetVerb("rep", "update").apply {
                addParameter("tag", tag)
                val what = JSONLiteralFactory.type("updatei", ForClientEncodeControl).apply {
                    addParameter("ref", ref)
                    addParameter("version", version)
                    addParameter("obj", obj.encode(ForRepositoryEncodeControl)!!.sendableString())
                    addParameterOpt("coll", collectionName)
                    finish()
                }
                addParameter("what", JSONLiteralArray.singleElementArray(what))
                finish()
            }
}
