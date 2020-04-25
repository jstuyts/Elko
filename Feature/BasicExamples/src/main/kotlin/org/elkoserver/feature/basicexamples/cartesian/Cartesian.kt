package org.elkoserver.feature.basicexamples.cartesian

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.BasicObject
import org.elkoserver.server.context.ContainerValidity
import org.elkoserver.server.context.Item
import org.elkoserver.server.context.ItemMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User

/**
 * Mod to provide an item with 2D (rectangular) geometry.  This mod may only be
 * attached to an item, not to a context or a user.
 *
 * This mod keeps track of a position and a rectangular extent (e.g., a width
 * and a height).  Position is reckoned relative to the object's container.
 * Dimensions are specified by integers whose unit interpretation (e.g.,
 * pixels, inches, furlongs, attoparsecs, etc.) is left to the application.
 *
 * @param myWidth  Horizontal extent of the geometry.
 * @param myHeight  Vertical extent of the geometry.
 * @param myLeft  X coordinate of object position relative to container.
 * @param myTop  Y coordinate of object position relative to container.
 */
class Cartesian @JSONMethod("width", "height", "left", "top") constructor(
        private val myWidth: Int,
        private val myHeight: Int,
        private var myLeft: Int,
        private var myTop: Int) : Mod(), ItemMod {

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("cart", control).apply {
                addParameter("width", myWidth)
                addParameter("height", myHeight)
                addParameter("left", myLeft)
                addParameter("top", myTop)
                finish()
            }

    /**
     * Message handler for the 'move' message.
     *
     * This message is a request from a client to move this object to a
     * different location and/or container.  If the move is successful, a
     * corresponding 'move' message is broadcast to the context.
     *
     * <u>recv</u>: ` { to:*REF*, op:"move", into:*optREF*,
     * left:*INT*, top:*INT* } `<br></br>
     *
     * <u>send</u>: ` { to:*REF*, op:"move", into:*optREF*,
     * left:*INT*, top:*INT* } `
     *
     * @param from  The user who sent the message.
     * @param into  Container into which object should be placed (optional,
     * defaults to same container, i.e., to leaving the container
     * unchanged).
     * @param left  X coordinate of new position relative to container.
     * @param top  Y coordinate of new position relative to container.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     * this mod or if the proposed destination container is invalid.
     */
    @JSONMethod("into", "left", "top")
    fun move(from: User?, into: OptString, left: Int, top: Int) {
        ensureSameContext(from)
        val item = `object`() as Item
        var newContainer: BasicObject? = null
        val newContainerRef = into.value(null)
        if (newContainerRef != null) {
            newContainer = context()[newContainerRef]
            if (!ContainerValidity.validContainer(newContainer, from)) {
                throw MessageHandlerException(
                        "invalid move destination container $newContainerRef")
            }
            item.setContainer(newContainer)
        }
        myLeft = left
        myTop = top
        markAsChanged()
        context().send(msgMove(item, newContainer, left, top))
    }

    companion object {
        /**
         * Create a 'move' message.
         *
         * @param target  Object the message is being sent to.
         * @param into  Container object is to be placed into (null if container is
         * not to be changed).
         * @param left  X coordinate of new position relative to container.
         * @param top  Y coordinate of new position relative to container.
         */
        private fun msgMove(target: Referenceable, into: BasicObject?, left: Int, top: Int) =
                JSONLiteralFactory.targetVerb(target, "move").apply {
                    addParameterOpt("into", into as Referenceable?)
                    addParameter("left", left)
                    addParameter("top", top)
                    finish()
                }
    }
}
