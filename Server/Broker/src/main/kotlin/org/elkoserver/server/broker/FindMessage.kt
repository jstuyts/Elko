package org.elkoserver.server.broker

import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'find' message.
 */
internal fun msgFind(target: Referenceable, desc: JsonLiteralArray, tag: String?) =
        JsonLiteralFactory.targetVerb(target, "find").apply {
            addParameter("desc", desc)
            addParameterOpt("tag", tag)
            finish()
        }
