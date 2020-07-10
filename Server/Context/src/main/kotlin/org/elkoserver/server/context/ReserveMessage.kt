package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'reserve' message.
 *
 * @param target  Object the message is being sent to.
 * @param protocol  The desired protocol for the reservation
 * @param contextRef  The context the reservation is sought for
 * @param userRef  The user for whom the reservation is sought
 * @param tag  Tag for matching responses with requests
 */
internal fun msgReserve(target: Referenceable, protocol: String, contextRef: String, userRef: String, tag: String) =
        JsonLiteralFactory.targetVerb(target, "reserve").apply {
            addParameter("protocol", protocol)
            addParameter("context", contextRef)
            addParameterOpt("user", userRef)
            addParameterOpt("tag", tag)
            finish()
        }
