package org.elkoserver.feature.basicexamples.notes

import org.elkoserver.feature.basicexamples.styledtext.StyleDesc
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'edit' message.
 *
 * @param target  Object the message is being sent to.
 * @param text  New text string value, or null if not being changed.
 * @param style  New style information, or null if not being changed.
 */
internal fun msgEdit(target: Referenceable, text: String?, style: StyleDesc?) =
        JsonLiteralFactory.targetVerb(target, "edit").apply {
            addParameterOpt("text", text)
            addParameterOpt("style", style)
            finish()
        }
