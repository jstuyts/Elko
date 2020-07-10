package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create an 'exit' message.
 *
 * @param target  Object the message is being sent to.
 * @param why  Helpful text explaining the reason for the exit.
 * @param whyCode  Machine readable tag indicating the reason for the exit.
 * @param reload  True if client should attempt a reload.
 */
fun msgExit(target: Referenceable, why: String?, whyCode: String?, reload: Boolean): JsonLiteral =
        JsonLiteralFactory.targetVerb(target, "exit").apply {
            addParameterOpt("why", why)
            addParameterOpt("whycode", whyCode)
            if (reload) {
                addParameter("reload", reload)
            }
            finish()
        }
