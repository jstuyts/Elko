package org.elkoserver.foundation.server

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'find' message: ask the broker to look up service information.
 * @param target  Object the message is being sent to.
 * @param service  The service being requested.
 * @param monitor  If true, broker should keep watching for additional
 * matches for the requested service.
 * @param tag  Optional tag to match response with the request.
 */
internal fun msgFind(target: Referenceable, service: String, monitor: Boolean, tag: String) =
        JsonLiteralFactory.targetVerb(target, "find").apply {
            addParameter("service", service)
            addParameter("wait", -1)
            if (monitor) {
                addParameter("monitor", true)
            }
            addParameterOpt("tag", tag)
            finish()
        }
