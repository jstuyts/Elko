package org.elkoserver.server.context.model

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'pushcontext' message.
 *
 * @param target  Object the message is being sent to
 * @param contextRef  Ref of the context to which the user is being sent
 * @param hostPort  Host:port of the context server they should use
 * @param reservation  Reservation code to tender to gain entry
 */
internal fun msgPushContext(target: Referenceable, contextRef: String, hostPort: String?, reservation: String?) =
        JsonLiteralFactory.targetVerb(target, "pushcontext").apply {
            addParameter("context", contextRef)
            addParameterOpt("hostport", hostPort)
            addParameterOpt("reservation", reservation)
            finish()
        }
