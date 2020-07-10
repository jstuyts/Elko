package org.elkoserver.feature.basicexamples.chat

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'style' message.
 *
 * @param target          Object the message is being sent to.
 * @param color           New text color value, or null if not being changed.
 * @param backgroundColor New background color value, or null if not
 * being changed
 * @param icon            New icon URL string, or null if not being changed.
 * @param textStyle       New typeface/style info, or null if not being changed.
 */
internal fun msgStyle(target: Referenceable, color: String?,
                      backgroundColor: String?, icon: String?, textStyle: String?) =
        JsonLiteralFactory.targetVerb(target, "style").apply {
            addParameterOpt("color", color)
            addParameterOpt("backgroundColor", backgroundColor)
            addParameterOpt("icon", icon)
            addParameterOpt("textStyle", textStyle)
            finish()
        }
