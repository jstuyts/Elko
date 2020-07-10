package org.elkoserver.foundation.actor

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'pong' message.
 *
 * @param target  Object the message is being sent to.
 * @param tag  Tag string (nominally from the 'ping' message that
 * triggered this) or null.
 */
internal fun msgPong(target: Referenceable, tag: String?) =
        JsonLiteralFactory.targetVerb(target, "pong").apply {
            addParameterOpt("tag", tag)
            finish()
        }
