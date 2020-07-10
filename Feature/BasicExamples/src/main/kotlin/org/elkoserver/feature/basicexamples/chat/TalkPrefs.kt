package org.elkoserver.feature.basicexamples.chat

import org.elkoserver.feature.basicexamples.styledtext.StyleDesc
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.ObjectCompletionWatcher
import org.elkoserver.server.context.User
import org.elkoserver.server.context.UserMod

/**
 * Mod to hold a user's current chat text display style settings.  This is used
 * to allow text chat to be conducted with user-configurable styled text.  It
 * operates in conjunction with [TalkOptions] and [Chat] mods that
 * should be attached to the context the user is in.
 *
 * This mod gets attached to a user, but note that it is not normally attached
 * to the user record in the object database.  It does not persist, but instead
 * is attached dynamically by the [TalkOptions] mod.
 *
 * Note that although this method takes a 'style'
 * parameter, normally styles are initialized (in the [ ][.objectIsComplete] method) by choosing, in a round-robin fashion, from
 * the style options available in in the context's [TalkOptions] mod.
 *
 * @param myStyle The [StyleDesc] associated with the chat text of the
 * user to whom this mod is attached.
 */
class TalkPrefs @JsonMethod("style") constructor(private var myStyle: StyleDesc) : Mod(), ObjectCompletionWatcher, UserMod {

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control Encode control determining what flavor of encoding
     * should be done.
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl): JsonLiteral? =
            if (control.toClient()) {
                JsonLiteralFactory.type("talkprefs", control).apply {
                    addParameter("style", myStyle)
                    finish()
                }
            } else {
                null
            }

    /**
     * Upon completion of the user object to which this mod attached, grab the
     * next set of available style choices from the context's [ ] mod.
     *
     * Application code should not call this method.
     */
    override fun objectIsComplete() {
        val rules = context().getMod(TalkOptions::class.java)
        if (rules != null) {
            myStyle = rules.newStyle()
        }
    }

    /**
     * Message handler for the 'style' message.
     *
     * This is a request from a client to change one or more of the style
     * attributes.  If the change operation is successful, a corresponding
     * 'style' message is broadcast to the context.
     *
     * <u>recv</u>: ` { to:*REF*, op:"style", color:*optSTR*,
     * backgroundColor:*optSTR*, icon:*optSTR*,
     * textStyle:*optSTR* } `<br></br>
     *
     * <u>send</u>: ` { to:*REF*, op:"style", color:*optSTR*,
     * backgroundColor:*optSTR*, icon:*optSTR*,
     * textStyle:*optSTR* } `
     *
     * @param from            The user who sent the message.
     * @param color           New text color value (optional).
     * @param backgroundColor New background color value (optional).
     * @param icon            New icon URL string (optional).
     * @param textStyle       New typeface/style info (optional).
     * @throws MessageHandlerException if 'from' is not in the same user this
     * mod is attached to.
     */
    @JsonMethod("color", "backgroundColor", "icon", "textStyle")
    fun style(from: User, color: OptString, backgroundColor: OptString,
              icon: OptString, textStyle: OptString) {
        ensureSameUser(from)
        val newColor = color.value<String?>(null)
        val newBackgroundColor = backgroundColor.value<String?>(null)
        val newIcon = icon.value<String?>(null)
        val newTextStyle = textStyle.value<String?>(null)
        val style = StyleDesc(newColor ?: myStyle.color,
                newBackgroundColor ?: myStyle.backgroundColor,
                null,
                newTextStyle ?: myStyle.textStyle,
                newIcon ?: myStyle.icon)
        val rules = context().getMod(TalkOptions::class.java)
        if (rules == null || rules.allowedStyle(style)) {
            myStyle = style
            context().send(msgStyle(`object`(), newColor, newBackgroundColor,
                    newIcon, newTextStyle))
        }
    }

    companion object {
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
        private fun msgStyle(target: Referenceable, color: String?,
                             backgroundColor: String?, icon: String?, textStyle: String?) =
                JsonLiteralFactory.targetVerb(target, "style").apply {
                    addParameterOpt("color", color)
                    addParameterOpt("backgroundColor", backgroundColor)
                    addParameterOpt("icon", icon)
                    addParameterOpt("textStyle", textStyle)
                    finish()
                }
    }
}
