package org.elkoserver.objectdatabase

import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.singleElementArray

/**
 * Fill in this request's message field with a 'remove' request.
 *
 * @param ref  Reference string naming the object to remove.
 * take the configured default (or the db doesn't use this abstraction).
 */
internal fun msgRemove(ref: String, tag: String) =
        JsonLiteralFactory.targetVerb("rep", "remove").apply {
            addParameter("tag", tag)
            @Suppress("SpellCheckingInspection")
            val what = JsonLiteralFactory.type("reqi", EncodeControl.ForClientEncodeControl).apply {
                addParameter("ref", ref)
                finish()
            }
            addParameter("what", singleElementArray(what))
            finish()
        }
