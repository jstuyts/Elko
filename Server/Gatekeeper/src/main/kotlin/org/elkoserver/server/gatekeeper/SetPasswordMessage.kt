package org.elkoserver.server.gatekeeper

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create 'setpassword' reply message.
 *
 * @param target  Object the message is being sent to.
 * @param id  Actor whose password was requested to be changed.
 * @param failure  Error message, or null if no error.
 */
internal fun msgSetPassword(target: Referenceable, id: String, failure: String?) =
        JsonLiteralFactory.targetVerb(target, "setpassword").apply {
            addParameter("id", id)
            addParameterOpt("failure", failure)
            finish()
        }
