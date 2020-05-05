package org.elkoserver.feature.basicexamples.notes

import org.elkoserver.feature.basicexamples.cartesian.Cartesian
import org.elkoserver.feature.basicexamples.styledtext.StyleDesc
import org.elkoserver.feature.basicexamples.styledtext.StyleOptions
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.context.GeneralMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.Msg
import org.elkoserver.server.context.User
import org.elkoserver.server.context.validContainer
import kotlin.contracts.ExperimentalContracts

/**
 * Mod to enable creation of notes.  Notes are items with the [Note] mod
 * attached.  This mod is generally attached to a context, but this is not
 * required.
 *
 * @param myStyleOptions  Style options permitted in notes created by this
 *    mod.
 */
class NoteMaker @JSONMethod("styles") constructor(private val myStyleOptions: StyleOptions) : Mod(), GeneralMod {
    /**
     * Test if this mod's style options are compatible with particular style
     * settings.
     *
     * @param style  The style to test.
     *
     * @return true if 'style' is acceptable to this NoteMaker, false if not.
     */
    fun allowedStyle(style: StyleDesc): Boolean = myStyleOptions.allowedStyle(style)

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl): JSONLiteral {
        val result = JSONLiteralFactory.type("notemaker", control)
        result.addParameter("styles", myStyleOptions)
        result.finish()
        return result
    }

    /**
     * Message handler for the 'makenote' message.
     *
     *
     * This is a request from a client to create a new note.  If the
     * creation operation is successful, a 'make' message will be broadcast to
     * the context, describing the new note object.
     *
     *
     *
     * <u>recv</u>: ` { to:*REF*, op:"makeNote", into:*optREF*,
     * left:*INT*, top:*INT*, width:*INT*,
     * height:*INT*, text:*STR*,
     * style:*optSTYLE* } `<br></br>
     *
     * <u>send</u>: ` { to:*intoREF*, op:"make", ... } `
     *
     * @param from  The user who sent the message.
     * @param into  Container into which the new note object should be placed
     * (optional, defaults to the context).
     * @param left  X coordinate of new object relative to container.
     * @param top  Y coordinate of new object relative to container.
     * @param width  Width of note display.
     * @param height  Height of note display.
     * @param text  The text to show.
     * @param style  Style information for 'text' (optional, defaults to
     * unstyled text).
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     * this mod or if invalid style information is provided or if the
     * proposed container is not valid.
     */
    @ExperimentalContracts
    @JSONMethod("into", "left", "top", "width", "height", "text", "?style")
    fun makenote(from: User, into: OptString, left: Int, top: Int,
                 width: Int, height: Int, text: String, style: StyleDesc?) {
        ensureSameContext(from)
        val intoRef = into.value<String?>(null)
        val intoObj = if (intoRef != null) {
            context()[intoRef]
        } else {
            context()
        }
        val mergedStyle = myStyleOptions.mergeStyle(style)
        if (mergedStyle == null) {
            throw MessageHandlerException("invalid style options")
        } else if (!validContainer(intoObj, from)) {
            throw MessageHandlerException("invalid destination container $intoRef")
        }
        val item = intoObj.createItem("note", false, true)
        Note(text, mergedStyle).run {
            attachTo(item)
        }
        Cartesian(width, height, left, top).run {
            attachTo(item)
        }
        item.objectIsComplete()
        context().send(Msg.msgMake(intoObj, item, from))
    }

}
