package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'user' message.
 */
internal fun msgUser(target: Referenceable, userName: String, online: Boolean, contexts: JsonLiteralArray?) =
        JsonLiteralFactory.targetVerb(target, "user").apply {
            addParameter("user", userName)
            addParameter("on", online)
            addParameterOpt("contexts", contexts)
            finish()
        }
