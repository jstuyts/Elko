package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'listcontexts' message.
 */
internal fun msgListcontexts(target: Referenceable, contexts: JsonLiteralArray) =
        JsonLiteralFactory.targetVerb(target, "listcontexts").apply {
            addParameter("contexts", contexts)
            finish()
        }
