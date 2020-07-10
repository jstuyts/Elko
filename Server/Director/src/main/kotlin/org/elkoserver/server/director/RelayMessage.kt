package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.JsonObject
import org.elkoserver.json.Referenceable

/**
 * Generate a 'relay' message.
 */
internal fun msgRelay(target: Referenceable, contextName: String?, userName: String?, relay: JsonObject) =
        JsonLiteralFactory.targetVerb(target, "relay").apply {
            addParameterOpt("context", contextName)
            addParameterOpt("user", userName)
            addParameter("msg", relay)
            finish()
        }
