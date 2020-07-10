package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteralFactory

/**
 * Create a "gate" message.
 *
 * @param context  The context whose gate is being indicated
 * @param open  Flag indicating open or closed
 * @param reason  Reason for closing the gate
 */
internal fun msgGate(context: String, open: Boolean, reason: String?) =
        JsonLiteralFactory.targetVerb("provider", "gate").apply {
            addParameter("context", context)
            addParameter("open", open)
            addParameterOpt("reason", reason)
            finish()
        }
