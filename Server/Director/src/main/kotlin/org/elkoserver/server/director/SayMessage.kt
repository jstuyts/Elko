package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'say' message.
 */
internal fun msgSay(target: Referenceable, contextName: String?, userName: String?, text: String) =
        JsonLiteralFactory.targetVerb(target, "say").apply {
            addParameterOpt("context", contextName)
            addParameterOpt("user", userName)
            addParameter("text", text)
            finish()
        }
