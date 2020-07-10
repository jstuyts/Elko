package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'listproviders' message.
 */
internal fun msgListproviders(target: Referenceable, providers: JsonLiteralArray) =
        JsonLiteralFactory.targetVerb(target, "listproviders").apply {
            addParameter("providers", providers)
            finish()
        }
