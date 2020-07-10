package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory

/**
 * Create a "user" message.
 *
 * @param context  The context entered or exited.
 * @param user  Who entered or exited.
 * @param on  Flag indicating online or offline.
 */
internal fun msgUser(context: String, user: String, on: Boolean) =
        JsonLiteralFactory.targetVerb("provider", "user").apply {
            addParameter("context", context)
            addParameter("user", user)
            addParameter("on", on)
            finish()
        }

/**
 * Create a "user" message.
 *
 * @param context  The context entered or exited.
 * @param user  Who entered or exited.
 * @param on  Flag indicating online or offline.
 */
internal fun msgUser(context: String, user: String, on: Boolean, userMeta: JsonLiteral, contextMeta: JsonLiteral) =
        JsonLiteralFactory.targetVerb("presence", "user").apply {
            addParameter("context", context)
            addParameter("user", user)
            addParameter("on", on)
            addParameterOpt("umeta", userMeta)
            addParameterOpt("cmeta", contextMeta)
            finish()
        }
