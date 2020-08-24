package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'ready' message.
 *
 * @param target  Object the message is being sent to.
 */
fun msgReady(target: Referenceable): JsonLiteral =
        JsonLiteralFactory.targetVerb(target, "ready").apply(JsonLiteral::finish)
