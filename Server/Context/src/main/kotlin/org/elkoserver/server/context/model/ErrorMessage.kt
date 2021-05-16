package org.elkoserver.server.context.model

import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create an 'error' message.  This informs the client that something went
 * wrong.
 *
 * @param target  Object the message is being sent to (the object being
 * informed).
 * @param op  Operation to be performed.
 * @param error  Contents of the error message.
 */
fun msgError(target: Referenceable, op: String, error: String?): JsonLiteral =
        JsonLiteralFactory.targetVerb(target, op).apply {
            addParameter("error", error)
            finish()
        }
