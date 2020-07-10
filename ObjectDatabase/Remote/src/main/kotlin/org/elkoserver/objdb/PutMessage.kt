package org.elkoserver.objdb

import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.singleElementArray

/**
 * Fill in this request's message field with a 'put' request.
 *
 * @param ref  Reference string naming the object to be put.
 * @param obj  The object itself.
 * @param collectionName  Name of collection to write, or null to take the
 * configured default (or the db doesn't use this abstraction).
 * @param requireNew  If true, require object 'ref' not already exist.
 */
internal fun msgPut(ref: String, tag: String, obj: Encodable, collectionName: String?, requireNew: Boolean) =
        JsonLiteralFactory.targetVerb("rep", "put").apply {
            addParameter("tag", tag)
            val what = JsonLiteralFactory.type("obji", EncodeControl.ForClientEncodeControl).apply {
                addParameter("ref", ref)
                addParameter("obj", obj.encode(EncodeControl.ForRepositoryEncodeControl)!!.sendableString())
                addParameterOpt("coll", collectionName)
                if (requireNew) {
                    addParameter("requirenew", requireNew)
                }
                finish()
            }
            addParameter("what", singleElementArray(what))
            finish()
        }
