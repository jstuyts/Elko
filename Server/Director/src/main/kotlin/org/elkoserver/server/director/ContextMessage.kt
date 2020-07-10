package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'context' message.
 */
internal fun msgContext(target: Referenceable, contextName: String?, open: Boolean, provider: String?, clones: JsonLiteralArray?) =
        JsonLiteralFactory.targetVerb(target, "context").apply {
            addParameter("context", contextName)
            addParameter("open", open)
            addParameterOpt("provider", provider)
            addParameterOpt("clones", clones)
            finish()
        }
