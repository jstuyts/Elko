package org.elkoserver.foundation.actor

import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create an 'auth' message.
 *
 * @param target  Object the message is being sent to.
 * @param auth  Authentication information to use.
 * @param label  Label to identify the entity seeking authorization.
 */
fun msgAuth(target: Referenceable, auth: AuthDesc?, label: String?): JsonLiteral = msgAuth(target.ref(), auth, label)

/**
 * Create an 'auth' message.
 *
 * @param target  Object the message is being sent to.
 * @param auth  Authentication information to use.
 * @param label  Label to identify the entity seeking authorization.
 */
fun msgAuth(target: String, auth: AuthDesc?, label: String?): JsonLiteral =
        JsonLiteralFactory.targetVerb(target, "auth").apply {
            addParameterOpt("auth", auth)
            addParameterOpt("label", label)
            finish()
        }
