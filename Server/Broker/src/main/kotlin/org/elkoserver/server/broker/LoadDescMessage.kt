package org.elkoserver.server.broker

import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'loaddesc' message.
 */
internal fun msgLoadDesc(target: Referenceable, desc: JsonLiteralArray?) =
        JsonLiteralFactory.targetVerb(target, "loaddesc").apply {
            addParameter("desc", desc)
            finish()
        }
