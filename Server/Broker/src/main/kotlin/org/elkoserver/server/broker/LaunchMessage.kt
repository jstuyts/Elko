package org.elkoserver.server.broker

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'launch' message.
 */
internal fun msgLaunch(target: Referenceable, status: String) =
        JsonLiteralFactory.targetVerb(target, "launch").apply {
            addParameter("status", status)
            finish()
        }
