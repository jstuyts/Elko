package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'reinit' message.
 */
internal fun msgReinit(target: Referenceable) =
        JsonLiteralFactory.targetVerb(target, "reinit").apply {
            finish()
        }
