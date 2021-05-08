package org.elkoserver.objectdatabase

import com.grack.nanojson.JsonObject
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.singleElementArray

/**
 * Fill in this request's message field with a 'query' request.
 *
 * @param template  Template object for the objects desired.
 * @param maxResults  Maximum number of result objects to return, or 0 to
 * indicate no fixed limit.
 */
internal fun msgQuery(template: JsonObject, tag: String, maxResults: Int) =
        JsonLiteralFactory.targetVerb("rep", "query").apply {
            addParameter("tag", tag)
            @Suppress("SpellCheckingInspection")
            val what = JsonLiteralFactory.type("queryi", EncodeControl.ForClientEncodeControl).apply {
                addParameter("template", template)
                if (maxResults > 0) {
                    addParameter("limit", maxResults)
                }
                finish()
            }
            addParameter("what", singleElementArray(what))
            finish()
        }
