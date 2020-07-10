package org.elkoserver.feature.basicexamples.chat

import org.elkoserver.feature.basicexamples.styledtext.StyleDesc
import org.elkoserver.feature.basicexamples.styledtext.StyleOptions
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.ContextMod
import org.elkoserver.server.context.Mod

/**
 * Mod to hold a context's permissible chat text display style options.  This
 * mod must be attached to a context.  It operates in conjunction with the
 * [TalkPrefs] and [Chat] mods.
 *
 * @param myStyles  Permissible styles for chat text in the context to which
 *    this mod is attached.
 */
class TalkOptions @JsonMethod("styles") constructor(private val myStyles: StyleOptions) : Mod(), ContextMod {
    private var myCounter = 0

    /**
     * Test if this mod's style options are compatible with particular style
     * settings.
     *
     * @param style  The [StyleDesc] to test.
     *
     * @return true if 'style' is acceptable to this object, false if not.
     */
    fun allowedStyle(style: StyleDesc) = myStyles.allowedStyle(style)

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl) =
            JsonLiteralFactory.type("talkoptions", control).apply {
                addParameter("styles", myStyles)
                finish()
            }

    /**
     * Get a set of style settings for a new user.  Successive calls to this
     * method will return a sequence of different style settings that step
     * through the various available style options in a round-robin fashion.
     *
     * @return a new [StyleDesc] object suitable for another user in the
     * context.
     */
    fun newStyle(): StyleDesc {
        var choices = myStyles.colors
        val color = if (choices == null) {
            null
        } else {
            choices[myCounter % choices.size]
        }
        choices = myStyles.backgroundColors
        val backgroundColor = if (choices == null) {
            null
        } else {
            choices[myCounter % choices.size]
        }
        choices = myStyles.textStyles
        val textStyle = if (choices == null) {
            null
        } else {
            choices[myCounter % choices.size]
        }
        choices = myStyles.icons
        val icon = if (choices == null) {
            null
        } else {
            choices[myCounter % choices.size]
        }
        ++myCounter
        return StyleDesc(color, backgroundColor, null, textStyle, icon)
    }
}
