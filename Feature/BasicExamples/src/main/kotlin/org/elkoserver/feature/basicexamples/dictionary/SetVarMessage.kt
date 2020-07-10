package org.elkoserver.feature.basicexamples.dictionary

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable


/**
 * Create a 'setvar' message.
 *
 * @param target  Object the message is being sent to.
 * @param from  Object the message is to be alleged to be from, or null if
 * not relevant.
 * @param names  Names of variables to change.
 * @param values  The values to change them to.
 */
internal fun msgSetvar(target: Referenceable, from: Referenceable, names: Array<String>, values: Array<String>) =
        JsonLiteralFactory.targetVerb(target, "setvar").apply {
            addParameterOpt("from", from)
            addParameter("names", names)
            addParameter("values", values)
            finish()
        }
