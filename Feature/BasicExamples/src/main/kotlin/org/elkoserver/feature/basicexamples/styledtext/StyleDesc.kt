package org.elkoserver.feature.basicexamples.styledtext

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory

/**
 * Representation of style information for something containing text.
 *
 * Note: this is not a mod.  StyleDesc objects are used by mods and by the
 * [StyleOptions] object.
 *
 * @param color  Foreground (text) color, or null if none.
 * @param backgroundColor  Background color, or null if none.
 * @param borderColor  Border color, or null if none.
 * @param textStyle Style string for text (e.g, "bold", "italic", etc.), or
 *    null if none.
 * @param icon URL of an icon to go with the text, or null if none.
 */
class StyleDesc(
        internal val color: String?,
        internal val backgroundColor: String?,
        internal val borderColor: String?,
        internal val textStyle: String?,
        internal val icon: String?) : Encodable {

    /**
     * JSON-driven constructor.
     *
     * @param color  Optional foreground (text) color.
     * @param backgroundColor  Optional background color.
     * @param borderColor  Optional border color.
     * @param textStyle  Optional style string (e.g., "bold", "italic", etc.)
     * for text.
     * @param icon  Optional URL of an icon to go with text.
     */
    @JSONMethod("color", "backgroundColor", "borderColor", "textStyle", "icon")
    constructor(color: OptString, backgroundColor: OptString,
                borderColor: OptString, textStyle: OptString,
                icon: OptString) : this(color.value<String?>(null), backgroundColor.value<String?>(null),
            borderColor.value<String?>(null), textStyle.value<String?>(null), icon.value<String?>(null))

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("style", control).apply {
                addParameterOpt("color", color)
                addParameterOpt("backgroundColor", backgroundColor)
                addParameterOpt("borderColor", borderColor)
                addParameterOpt("textStyle", textStyle)
                addParameterOpt("icon", icon)
                finish()
            }

    /**
     * Merge this StyleDesc with another, partially specified StyleDesc,
     * creating a new StyleDesc.
     *
     * @param partial  The (partial) StyleDesc to merge with
     *
     * @return a new StyleDesc with the settings of 'partial' where 'partial'
     * specifies them, and the settings of this object where 'partial' does
     * not specify them.
     */
    fun mergeStyle(partial: StyleDesc) =
            StyleDesc(overlay(partial.color, color),
                    overlay(partial.backgroundColor, backgroundColor),
                    overlay(partial.borderColor, borderColor),
                    overlay(partial.textStyle, textStyle),
                    overlay(partial.icon, icon))

    /**
     * Overlay a new value on an old one.
     *
     * @param newChoice  The new value.
     * @param oldChoice  The old value.
     *
     * @return 'oldChoice' if 'newChoice' is null, else 'newChoice'.
     */
    private fun overlay(newChoice: String?, oldChoice: String?) =
            newChoice ?: oldChoice
}
