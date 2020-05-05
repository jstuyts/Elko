package org.elkoserver.feature.basicexamples.styledtext

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory

/**
 * Representation of permissible text style information in a context that can
 * contain text.
 *
 * Note: this is not a mod.  StyleOptions objects are used by mods.
 *
 * @param myColors  Permissible foreground (text) colors.
 * @param myBackgroundColors  Permissible background colors.
 * @param myBorderColors  Permissible border colors.
 * @param myTextStyles  Permissible text styles.
 * @param myIcons  Permissible icon URLs.
 * @param iconWidth  Common width of icons, or -1 if not relevant.
 * @param iconHeight  Common height of icons, or -1 if not relevant.
 */
class StyleOptions @JSONMethod("colors", "backgroundColors", "borderColors", "textStyles", "icons", "iconWidth", "iconHeight") constructor(
        private val myColors: Array<String>,
        private val myBackgroundColors: Array<String>,
        private val myBorderColors: Array<String>,
        private val myTextStyles: Array<String>,
        private val myIcons: Array<String>,
        iconWidth: OptInteger,
        iconHeight: OptInteger) : Encodable {
    private val myIconWidth: Int = iconWidth.value(-1)
    private val myIconHeight: Int = iconHeight.value(-1)

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
        return allowedChoice(style.color(), myColors) &&
                allowedChoice(style.backgroundColor(), myBackgroundColors) &&
                allowedChoice(style.borderColor(), myBorderColors) &&
                allowedChoice(style.textStyle(), myTextStyles) &&
                allowedChoice(style.icon(), myIcons)
    }

    /**
     * Get the permissible background colors.
     *
     * @return an array of the permissible background colors.
     */
    fun backgroundColors(): Array<String>? = myBackgroundColors

    /**
     * Get the permissible border colors.
     *
     * @return an array of the permissible border colors.
     */
    fun borderColors(): Array<String>? = myBorderColors

    /**
     * Get the permissible foreground (text) colors.
     *
     * @return an array of the permissible foreground colors.
     */
    fun colors(): Array<String>? = myColors

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("styleoptions", control).apply {
                if (myColors != null && myColors.isNotEmpty()) {
                    addParameter("colors", myColors)
                }
                if (myBackgroundColors != null && myBackgroundColors.isNotEmpty()) {
                    addParameter("backgroundColors", myBackgroundColors)
                }
                if (myBorderColors != null && myBorderColors.isNotEmpty()) {
                    addParameter("borderColors", myBorderColors)
                }
                if (myTextStyles != null && myTextStyles.isNotEmpty()) {
                    addParameter("textStyles", myTextStyles)
                }
                if (myIcons != null && myIcons.isNotEmpty()) {
                    addParameter("icons", myIcons)
                }
                if (myIconWidth >= 0) {
                    addParameter("iconWidth", myIconWidth)
                }
                if (myIconHeight >= 0) {
                    addParameter("iconHeight", myIconHeight)
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
     * Get the permissible icon URLs.
     *
     * @return an array of the permissible icon URLs.
     */
    fun icons(): Array<String>? = myIcons

    /**
     * Get the height of the icons.
     *
     * @return the (common) height of the icons, or -1 if they do not have a
     * common height.
     */
    fun iconHeight(): Int = myIconHeight

    /**
     * Get the width of the icons.
     *
     * @return the (common) width of the icons, or -1 if they do not have a
     * common width.
     */
    fun iconWidth(): Int = myIconWidth

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
            backgroundColor = extract(null, myBackgroundColors)
            borderColor = extract(null, myBorderColors)
            color = extract(null, myColors)
            icon = extract(null, myIcons)
            textStyle = extract(null, myTextStyles)
        } else {
            backgroundColor = extract(style.backgroundColor(), myBackgroundColors)
            borderColor = extract(style.borderColor(), myBorderColors)
            color = extract(style.color(), myColors)
            icon = extract(style.icon(), myIcons)
            textStyle = extract(style.textStyle(), myTextStyles)
        }
        val result = StyleDesc(color, backgroundColor, borderColor, textStyle, icon)
        return if (allowedStyle(result)) {
            result
        } else {
            null
        }
    }

    /**
     * Get the permissible text styles.
     *
     * @return an array of the permissible text styles.
     */
    fun textStyles(): Array<String>? = myTextStyles
}
