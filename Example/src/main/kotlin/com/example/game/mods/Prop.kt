package com.example.game.mods

import org.elkoserver.feature.basicexamples.cartesian.CartesianPosition
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.Item
import org.elkoserver.server.context.ItemMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User
import org.elkoserver.server.context.msgDelete

class Prop @JsonMethod("kind") constructor(private val myKind: String) : Mod(), ItemMod {
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("prop", control).apply {
                addParameter("kind", myKind)
                finish()
            }

    @JsonMethod
    fun grab(from: User) {
        ensureInContext(from)
        val item = `object`() as Item
        assertIsPortable()
        val userPos = getUserPosition(from)
        val itemPos = getItemPosition(item, from)
        val dx = itemPos.x - userPos.x
        val dy = itemPos.y - userPos.y
        assertWithinGrabDistance(dx, dy, item)
        item.setContainer(from)
        itemPos.detach()
        context().sendToNeighbors(from, msgDelete(item))
        from.send(msgMove(item, 0, 0, from))
    }

    @JsonMethod
    fun drop(from: User) {
        ensureHolding(from)
        val item = `object`() as Item
        assertIsPortable()
        val userPos = getUserPosition(from)
        var itemPos = item.getMod(CartesianPosition::class.java)
        if (itemPos == null) {
            itemPos = CartesianPosition(userPos.x, userPos.y)
            itemPos.attachTo(item)
        } else {
            itemPos[userPos.x] = userPos.y
        }
        item.setContainer(context())
        item.sendObjectDescription(context().neighbors(from), context())
        from.send(msgMove(item, userPos.x, userPos.y, context()))
    }

    private fun assertIsPortable() {
        if (!(`object`() as Item).isPortable) {
            throw MessageHandlerException("attempt to grab/drop non-portable item ${`object`()}")
        }
    }

    private fun getUserPosition(from: User) =
            from.getMod(CartesianPosition::class.java)
                    ?: throw MessageHandlerException("user $from attempted grab/drop $this but Cartesian position mod not present on user")

    private fun getItemPosition(item: Item, from: User) =
            item.getMod(CartesianPosition::class.java)
                    ?: throw MessageHandlerException("user $from attempted grab $this but Cartesian position mod not present on item")

    private fun assertWithinGrabDistance(dx: Int, dy: Int, item: Item) {
        if (dx * dx + dy * dy > GRAB_DISTANCE * GRAB_DISTANCE) {
            throw MessageHandlerException("attempt to grab too far away item $item")
        }
    }

    companion object {
        private const val GRAB_DISTANCE = 5
    }
}
