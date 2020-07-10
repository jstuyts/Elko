package org.elkoserver.server.gatekeeper

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'director' message.
 */
internal fun msgDirector(target: Referenceable, hostport: String) =
        JsonLiteralFactory.targetVerb(target, "director").apply {
            addParameter("hostport", hostport)
            finish()
        }
