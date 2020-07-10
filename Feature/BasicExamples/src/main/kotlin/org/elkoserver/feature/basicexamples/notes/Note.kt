package org.elkoserver.feature.basicexamples.notes

import org.elkoserver.feature.basicexamples.styledtext.StyleDesc
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.ItemMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User

/**
 * Mod to hold a free-floating chunk of text.  This mod must be attached to an
 * item, not to a user or context.
 *
 * @param myText  The text of this note.
 * @param myStyle  How its text is to be displayed.
 */
class Note @JsonMethod("text", "style") constructor(private var myText: String, private var myStyle: StyleDesc) : Mod(), ItemMod {

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("note", control).apply {
                addParameter("text", myText)
                addParameterOpt("style", myStyle)
                finish()
            }

    /**
     * Message handler for the 'edit' message.
     *
     * This message is a request from a client to change the text of this
     * note or one or more of its style attributes.  If the change is
     * successful, a corresponding 'edit' message is broadcast to the
     * context.
     *
     * <u>recv</u>: ` { to:*REF*, op:"edit", text:*optSTR*,
     * style:*optSTYLE* } `<br></br>
     *
     * <u>send</u>: ` { to:*REF*, op:"edit", text:*optSTR*,
     * style:*optSTYLE* } `
     *
     * @param from  The user who sent the message.
     * @param text  New text string value (optional).
     * @param style  New style information (optional).
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     * this mod or if invalid style information is provided.
     */
    @JsonMethod("text", "?style")
    fun edit(from: User, text: OptString, style: StyleDesc?) {
        ensureSameContext(from)
        var actualStyle = style
        if (actualStyle != null) {
            actualStyle = myStyle.mergeStyle(actualStyle)
            val rules = context().getMod(NoteMaker::class.java)
            myStyle = if (rules != null && rules.allowedStyle(actualStyle)) {
                actualStyle
            } else {
                throw MessageHandlerException("invalid style choice")
            }
        }
        val newText = text.value<String?>(null)
        if (newText != null) {
            myText = newText
        }
        if (actualStyle != null || newText != null) {
            markAsChanged()
            context().send(msgEdit(`object`(), newText, actualStyle))
        }
    }
}
