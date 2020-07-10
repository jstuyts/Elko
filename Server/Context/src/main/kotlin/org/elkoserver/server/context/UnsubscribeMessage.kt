package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteralFactory

/**
 * Create an "unsubscribe" message.
 *
 * @param context  The context that is ceasing to subscribe
 */
internal fun msgUnsubscribe(context: String) =
        JsonLiteralFactory.targetVerb("presence", "unsubscribe").apply {
            addParameter("context", context)
            finish()
        }
