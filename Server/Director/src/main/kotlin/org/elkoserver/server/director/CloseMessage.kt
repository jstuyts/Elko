package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'close' message.
 */
internal fun msgClose(target: Referenceable, contextName: String?, userName: String?, isDup: Boolean) =
        JsonLiteralFactory.targetVerb(target, "close").apply {
            addParameterOpt("context", contextName)
            addParameterOpt("user", userName)
            if (isDup) {
                addParameter("dup", true)
            }
            finish()
        }
