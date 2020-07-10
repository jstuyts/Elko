package org.elkoserver.server.broker

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'shutdown' message.
 */
internal fun msgShutdown(target: Referenceable) =
        JsonLiteralFactory.targetVerb(target, "shutdown").apply {
            finish()
        }
