package org.elkoserver.objectdatabase

import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.singleElementArray

/**
 * Fill in this request's message field with an 'update' request.
 *
 * @param ref  Reference string naming the object to be put.
 * @param version  Version number of the version of the object to update.
 * @param obj  The object itself.
 */
internal fun msgUpdate(ref: String, tag: String, version: Int, obj: Encodable) =
        JsonLiteralFactory.targetVerb("rep", "update").apply {
            addParameter("tag", tag)
            @Suppress("SpellCheckingInspection")
            val what = JsonLiteralFactory.type("updatei", EncodeControl.ForClientEncodeControl).apply {
                addParameter("ref", ref)
                addParameter("version", version)
                addParameter("obj", obj.encode(EncodeControl.ForRepositoryEncodeControl)!!.sendableString())
                finish()
            }
            addParameter("what", singleElementArray(what))
            finish()
        }
