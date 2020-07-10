package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'lookupactor' message.
 */
internal fun msgLookupActor(target: Referenceable, id: String, iid: String?, name: String?, failure: String?) =
        JsonLiteralFactory.targetVerb(target, "lookupactor").apply {
            addParameter("id", id)
            addParameterOpt("iid", iid)
            addParameterOpt("name", name)
            addParameterOpt("failure", failure)
            finish()
        }
