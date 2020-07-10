package org.elkoserver.feature.basicexamples.dictionary

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'delvar' message.
 *
 * @param target  Object the message is being sent to.
 * @param from  Object the message is to be alleged to be from, or null if
 * not relevant.
 * @param names  Names of the variables to delete.
 */
internal fun msgDelvar(target: Referenceable, from: Referenceable, names: Array<String>) =
        JsonLiteralFactory.targetVerb(target, "delvar").apply {
            addParameterOpt("from", from)
            addParameter("names", names)
            finish()
        }
