package org.elkoserver.server.broker

import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'reinit' message.
 */
internal fun msgReinit(target: Referenceable) =
        JsonLiteralFactory.targetVerb(target, "reinit").apply(JsonLiteral::finish)
