package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'deleteactor' message.
 */
internal fun msgDeleteActor(target: Referenceable, id: String, failure: String?) =
        JsonLiteralFactory.targetVerb(target, "deleteactor").apply {
            addParameter("id", id)
            addParameterOpt("failure", failure)
            finish()
        }
