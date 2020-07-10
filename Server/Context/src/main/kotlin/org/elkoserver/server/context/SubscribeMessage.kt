package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteralFactory

/**
 * Create a "subscribe" message.
 *
 * @param context  The context that is subscribing
 * @param domains  The presence domains being subscribed to
 * @param visible  Flag indicating if presence in the given context is
 * visible outside the context
 */
internal fun msgSubscribe(context: String, domains: Array<String>, visible: Boolean) =
        JsonLiteralFactory.targetVerb("presence", "subscribe").apply {
            addParameter("context", context)
            addParameter("domains", domains)
            addParameter("visible", visible)
            finish()
        }
