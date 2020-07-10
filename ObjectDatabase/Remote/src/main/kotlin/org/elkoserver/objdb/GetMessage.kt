package org.elkoserver.objdb

import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory

/**
 * Fill in this request's message field with a 'get' request.
 * @param ref  Reference string naming the object desired.
 * @param collectionName  Name of collection to get from, or null to take
 */
internal fun msgGet(ref: String, tag: String, collectionName: String?) =
        JsonLiteralFactory.targetVerb("rep", "get").apply {
            addParameter("tag", tag)
            val what = JsonLiteralFactory.type("reqi", EncodeControl.ForClientEncodeControl).apply {
                addParameter("ref", ref)
                addParameter("contents", true)
                addParameterOpt("coll", collectionName)
                finish()
            }
            addParameter("what", JsonLiteralArray.singleElementArray(what))
            finish()
        }
