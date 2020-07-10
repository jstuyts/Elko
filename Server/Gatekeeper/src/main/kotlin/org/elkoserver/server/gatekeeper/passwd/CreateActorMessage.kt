package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'createactor' message.
 */
internal fun msgCreateActor(target: Referenceable, id: String, failure: String?) =
        JsonLiteralFactory.targetVerb(target, "createactor").apply {
            addParameter("id", id)
            addParameterOpt("failure", failure)
            finish()
        }
