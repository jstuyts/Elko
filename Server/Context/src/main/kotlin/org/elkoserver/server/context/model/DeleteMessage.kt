package org.elkoserver.server.context.model

import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'delete' message.  This directs a client to delete an object.
 *
 * @param target  Object the message is being sent to (the object being
 * deleted).
 */
fun msgDelete(target: Referenceable): JsonLiteral =
        JsonLiteralFactory.targetVerb(target, "delete").apply(JsonLiteral::finish)
