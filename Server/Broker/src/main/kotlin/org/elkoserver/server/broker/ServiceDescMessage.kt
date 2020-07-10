package org.elkoserver.server.broker

import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'servicedesc' message.
 */
internal fun msgServiceDesc(target: Referenceable, desc: JsonLiteralArray?, on: Boolean) =
        JsonLiteralFactory.targetVerb(target, "servicedesc").apply {
            addParameter("desc", desc)
            addParameter("on", on)
            finish()
        }
