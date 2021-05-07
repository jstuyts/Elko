package org.elkoserver.objectdatabase

import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.singleElementArray

/**
 * Fill in this request's message field with a 'remove' request.
 *
 * @param ref  Reference string naming the object to remove.
 * @param collectionName  Name of collection to remove from, or null to
 * take the configured default (or the db doesn't use this abstraction).
 */
internal fun msgRemove(ref: String, tag: String, collectionName: String?) =
        JsonLiteralFactory.targetVerb("rep", "remove").apply {
            addParameter("tag", tag)
            val what = JsonLiteralFactory.type("reqi", EncodeControl.ForClientEncodeControl).apply {
                addParameter("ref", ref)
                addParameterOpt("coll", collectionName)
                finish()
            }
            addParameter("what", singleElementArray(what))
            finish()
        }
