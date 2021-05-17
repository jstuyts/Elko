package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'willserve' message.
 *
 * @param target  Object the message is being sent to.
 * @param context  The context family that will be served.
 * @param capacity  Maximum number of users that will be served.
 * @param restricted  True if the context family is restricted
 */
internal fun msgWillserve(target: Referenceable, context: String, capacity: Int, restricted: Boolean) =
        JsonLiteralFactory.targetVerb(target, "willserve").apply {
            addParameter("context", context)
            if (0 < capacity) {
                addParameter("capacity", capacity)
            }
            if (restricted) {
                addParameter("restricted", true)
            }
            finish()
        }
