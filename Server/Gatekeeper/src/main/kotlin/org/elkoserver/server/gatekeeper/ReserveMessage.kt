package org.elkoserver.server.gatekeeper

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'reserve' message.
 *
 * @param target  Object the message is being sent to.
 * @param protocol  Desired protocol.
 * @param context  Context to enter.
 * @param actor Who wants to enter.
 */
internal fun msgReserve(target: Referenceable, protocol: String,
                       context: String, actor: String?) =
        JsonLiteralFactory.targetVerb(target, "reserve").apply {
            addParameter("protocol", protocol)
            addParameter("context", context)
            addParameterOpt("user", actor)
            finish()
        }

/**
 * Create a 'reserve' reply message.
 *
 * @param target  Object the message is being sent to.
 * @param id  The ID for which the reservation was requested, or null if
 * none.
 * @param context  Context the reservation is for.
 * @param actor  Actor the reservation is for, or null for anonymous.
 * @param hostPort  Host:port to connect to, or null in error case.
 * @param auth  Authorization code for entry, or null in error case.
 * @param deny  Error message in error case, or null in normal case.
 */
internal fun msgReserve(target: Referenceable, id: String?, context: String?, actor: String?, name: String?, hostPort: String?, auth: String?, deny: String?) =
        JsonLiteralFactory.targetVerb(target, "reserve").apply {
            addParameterOpt("id", id)
            addParameter("context", context)
            addParameterOpt("actor", actor)
            addParameterOpt("name", name)
            addParameterOpt("hostport", hostPort)
            addParameterOpt("auth", auth)
            addParameterOpt("deny", deny)
            finish()
        }
