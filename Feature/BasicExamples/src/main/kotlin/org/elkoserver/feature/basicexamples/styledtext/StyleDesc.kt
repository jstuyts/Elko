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
 * @param myColor  Foreground (text) color, or null if none.
 * @param myBackgroundColor  Background color, or null if none.
 * @param myBorderColor  Border color, or null if none.
 * @param myTextStyle Style string for text (e.g, "bold", "italic", etc.), or
 *    null if none.
 * @param myIcon URL of an icon to go with the text, or null if none.
 */
class StyleDesc(
        private val myColor: String?,
        private val myBackgroundColor: String?,
        private val myBorderColor: String?,
        private val myTextStyle: String?,
        private val myIcon: String?) : Encodable {

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
            borderColor.value<String?>(null), textStyle.value<String?>(null), icon.value<String?>(null)) {
    }

    /**
     * Get the background color.
     *
     * @return this style's background color, or null if there is none.
     */
    fun backgroundColor(): String? {
        return myBackgroundColor
    }

    /**
     * Get the border color.
     *
     * @return this style's border color, or null if there is none.
     */
    fun borderColor(): String? {
        return myBorderColor
    }

    /**
     * Get the foreground (text) color.
     *
     * @return this style's foreground color, or null if there is none.
     */
    fun color(): String? {
        return myColor
    }

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
                addParameterOpt("color", myColor)
                addParameterOpt("backgroundColor", myBackgroundColor)
                addParameterOpt("borderColor", myBorderColor)
                addParameterOpt("textStyle", myTextStyle)
                addParameterOpt("icon", myIcon)
                finish()
            }

    /**
     * Get the icon URL.
     *
     * @return this style's icon URL, or null if there is none.
     */
    fun icon(): String? {
        return myIcon
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
            StyleDesc(overlay(partial.color(), myColor),
                    overlay(partial.backgroundColor(), myBackgroundColor),
                    overlay(partial.borderColor(), myBorderColor),
                    overlay(partial.textStyle(), myTextStyle),
                    overlay(partial.icon(), myIcon))

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

    /**
     * Get the text style for this StyleDesc.  This is a string that specifies
     * attributes such as typeface, bold, italic, etc.
     *
     * @return this style's text style string, or null if there is none.
     */
    fun textStyle(): String? {
        return myTextStyle
    }
}
