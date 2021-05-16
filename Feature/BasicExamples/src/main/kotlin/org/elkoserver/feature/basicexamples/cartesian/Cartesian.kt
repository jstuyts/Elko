package org.elkoserver.feature.basicexamples.cartesian

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.model.BasicObject
import org.elkoserver.server.context.model.Item
import org.elkoserver.server.context.model.ItemMod
import org.elkoserver.server.context.model.Mod
import org.elkoserver.server.context.model.User
import org.elkoserver.server.context.model.validContainer
import kotlin.contracts.ExperimentalContracts

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
class Cartesian @JsonMethod("width", "height", "left", "top") constructor(
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
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("cart", control).apply {
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
    @ExperimentalContracts
    @JsonMethod("into", "left", "top")
    fun move(from: User, into: OptString, left: Int, top: Int) {
        ensureSameContext(from)
        val item = `object`() as Item
        var newContainer: BasicObject? = null
        val newContainerRef = into.valueOrNull()
        if (newContainerRef != null) {
            newContainer = context()[newContainerRef]
            if (!validContainer(newContainer, from)) {
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
}
