package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory

/**
 * Create a "relay" message.
 *
 * @param target  The message target.
 * @param contextName  The base name of the context to relay to.
 * @param userName  The base name of the user to relay to.
 * @param relay  The message to relay.
 */
internal fun msgRelay(target: String, contextName: String?, userName: String?, relay: JsonLiteral) =
        JsonLiteralFactory.targetVerb(target, "relay").apply {
            addParameterOpt("context", contextName)
            addParameterOpt("user", userName)
            addParameter("msg", relay)
            finish()
        }
