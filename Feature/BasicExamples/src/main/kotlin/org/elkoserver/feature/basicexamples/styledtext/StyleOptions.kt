package org.elkoserver.feature.basicexamples.styledtext

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory

/**
 * Representation of permissible text style information in a context that can
 * contain text.
 *
 * Note: this is not a mod.  StyleOptions objects are used by mods.
 *
 * @param colors  Permissible foreground (text) colors.
 * @param backgroundColors  Permissible background colors.
 * @param borderColors  Permissible border colors.
 * @param textStyles  Permissible text styles.
 * @param icons  Permissible icon URLs.
 * @param theIconWidth  Common width of icons, or -1 if not relevant.
 * @param theIconHeight  Common height of icons, or -1 if not relevant.
 */
class StyleOptions @JsonMethod("colors", "backgroundColors", "borderColors", "textStyles", "icons", "iconWidth", "iconHeight") constructor(
        internal val colors: Array<String>,
        internal val backgroundColors: Array<String>,
        private val borderColors: Array<String>,
        internal val textStyles: Array<String>,
        internal val icons: Array<String>,
        theIconWidth: OptInteger,
        theIconHeight: OptInteger) : Encodable {
    private val iconWidth: Int = theIconWidth.value(-1)
    private val iconHeight: Int = theIconHeight.value(-1)

    /**
     * Test if a particular string is a member of an array of allowed choices.
     *
     * @param choice  The string to test.
     * @param choices  The array to check it against.
     *
     * @return true if 'choice' is in 'choices'.
     */
    private fun allowedChoice(choice: String?, choices: Array<String>?): Boolean {
        return if (choices == null || choices.isEmpty()) {
            choice == null
        } else {
            for (test in choices) {
                if (choice == test) {
                    return true
                }
            }
            false
        }
    }

    /**
     * Test if a particular [StyleDesc] is permissible according to this
     * object's settings.
     *
     * @param style  The [StyleDesc] to test.
     *
     * @return true if 'style' is acceptable to this object, false if not.
     */
    fun allowedStyle(style: StyleDesc): Boolean {
        return allowedChoice(style.color, colors) &&
                allowedChoice(style.backgroundColor, backgroundColors) &&
                allowedChoice(style.borderColor, borderColors) &&
                allowedChoice(style.textStyle, textStyles) &&
                allowedChoice(style.icon, icons)
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
            JsonLiteralFactory.type("styleoptions", control).apply {
                if (colors != null && colors.isNotEmpty()) {
                    addParameter("colors", colors)
                }
                if (backgroundColors != null && backgroundColors.isNotEmpty()) {
                    addParameter("backgroundColors", backgroundColors)
                }
                if (borderColors != null && borderColors.isNotEmpty()) {
                    addParameter("borderColors", borderColors)
                }
                if (textStyles != null && textStyles.isNotEmpty()) {
                    addParameter("textStyles", textStyles)
                }
                if (icons != null && icons.isNotEmpty()) {
                    addParameter("icons", icons)
                }
                if (iconWidth >= 0) {
                    addParameter("iconWidth", iconWidth)
                }
                if (iconHeight >= 0) {
                    addParameter("iconHeight", iconHeight)
                }
                finish()
            }

    /**
     * Extract a choice from an array of choices.
     *
     * @param choice  The choice to extract, or null if the default is sought
     * @param choices   Array of allowed choices.
     *
     * @return 'choice' if 'choice' is not null, else the default if 'choice'
     * selects the default by being null.
     */
    private fun extract(choice: String?, choices: Array<String>?): String? {
        return choice ?: if (choices == null || choices.isEmpty()) {
            null
        } else {
            choices[0]
        }
    }

    /**
     * Produce a new [StyleDesc] object given another, partially
     * specified, [StyleDesc] object.
     *
     * @param style  The [StyleDesc] to start from.
     *
     * @return a new [StyleDesc] object that is a copy of 'style' with
     * additional attributes according to the defaults contained in this
     * object, or null if one of the attributes specified by 'style' is not
     * permitted by this object's settings.
     */
    fun mergeStyle(style: StyleDesc?): StyleDesc? {
        val backgroundColor: String?
        val borderColor: String?
        val color: String?
        val icon: String?
        val textStyle: String?
        if (style == null) {
            backgroundColor = extract(null, backgroundColors)
            borderColor = extract(null, borderColors)
            color = extract(null, colors)
            icon = extract(null, icons)
            textStyle = extract(null, textStyles)
        } else {
            backgroundColor = extract(style.backgroundColor, backgroundColors)
            borderColor = extract(style.borderColor, borderColors)
            color = extract(style.color, colors)
            icon = extract(style.icon, icons)
            textStyle = extract(style.textStyle, textStyles)
        }
        val result = StyleDesc(color, backgroundColor, borderColor, textStyle, icon)
        return if (allowedStyle(result)) {
            result
        } else {
            null
        }
    }
}
