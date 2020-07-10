package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteralFactory

/**
 * Create a "context" message.
 *
 * @param context  The context opened or closed.
 * @param open  Flag indicating open or closed.
 * @param yours  Flag indicating if recipient was controlling director.
 * @param maxCapacity   Max # users in context, or -1 for no limit.
 * @param baseCapacity   Max # users before cloning, or -1 for no limit.
 * @param restricted  Flag indicated if context is restricted
 */
internal fun msgContext(context: String, open: Boolean, yours: Boolean, maxCapacity: Int, baseCapacity: Int, restricted: Boolean) =
        JsonLiteralFactory.targetVerb("provider", "context").apply {
            addParameter("context", context)
            addParameter("open", open)
            addParameter("yours", yours)
            if (open) {
                if (maxCapacity != -1) {
                    addParameter("maxcap", maxCapacity)
                }
                if (baseCapacity != -1) {
                    addParameter("basecap", baseCapacity)
                }
                if (restricted) {
                    addParameter("restricted", restricted)
                }
            }
            finish()
        }
