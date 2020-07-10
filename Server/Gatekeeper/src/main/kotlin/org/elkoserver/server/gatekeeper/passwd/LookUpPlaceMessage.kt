package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'lookupplace' message.
 */
internal fun msgLookupPlace(target: Referenceable, name: String, context: String?) =
        JsonLiteralFactory.targetVerb(target, "lookupplace").apply {
            addParameter("name", name)
            addParameterOpt("context", context)
            finish()
        }
