package com.example.game.mods

import com.example.game.mods.Movement.Companion.msgMove
import org.elkoserver.feature.basicexamples.cartesian.CartesianPosition
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.context.Item
import org.elkoserver.server.context.ItemMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.Msg
import org.elkoserver.server.context.User

class Prop @JSONMethod("kind") constructor(private val myKind: String) : Mod(), ItemMod {
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("prop", control).apply {
                addParameter("kind", myKind)
                finish()
            }

    @JSONMethod
    fun grab(from: User) {
        ensureInContext(from)
        val item = `object`() as Item
        assertIsPortable()
        val userPos = getUserPosition(from)
        val itemPos = getItemPosition(item, from)
        val dx = itemPos.x() - userPos.x()
        val dy = itemPos.y() - userPos.y()
        assertWithinGrabDistance(dx, dy, item)
        item.setContainer(from)
        itemPos.detach()
        context().sendToNeighbors(from, Msg.msgDelete(item))
        from.send(msgMove(item, 0, 0, from))
    }

    @JSONMethod
    fun drop(from: User) {
        ensureHolding(from)
        val item = `object`() as Item
        assertIsPortable()
        val userPos = getUserPosition(from)
        var itemPos = item.getMod(CartesianPosition::class.java)
        if (itemPos == null) {
            itemPos = CartesianPosition(userPos.x(), userPos.y())
            itemPos.attachTo(item)
        } else {
            itemPos[userPos.x()] = userPos.y()
        }
        item.setContainer(context())
        item.sendObjectDescription(context().neighbors(from), context())
        from.send(msgMove(item, userPos.x(), userPos.y(), context()))
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
