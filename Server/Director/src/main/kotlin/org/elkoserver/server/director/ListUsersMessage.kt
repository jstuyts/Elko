package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'listusers' message.
 */
internal fun msgListusers(target: Referenceable, users: JsonLiteralArray) =
        JsonLiteralFactory.targetVerb(target, "listusers").apply {
            addParameter("users", users)
            finish()
        }
