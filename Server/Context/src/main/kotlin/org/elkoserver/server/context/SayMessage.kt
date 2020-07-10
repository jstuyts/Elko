package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'say' message.  This directs a client to display chat text.
 *
 * @param target  Object the message is being sent to (normally this will
 * be a user or context).
 * @param from  Object the message is to be alleged to be from, or null if
 * not relevant.  This normally indicates the user who is speaking.
 * @param text  The text to be said.
 */
fun msgSay(target: Referenceable, from: Referenceable?, text: String?): JsonLiteral =
        JsonLiteralFactory.targetVerb(target, "say").apply {
            addParameterOpt("from", from)
            addParameter("text", text)
            finish()
        }
