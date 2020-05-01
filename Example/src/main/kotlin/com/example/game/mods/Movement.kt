package com.example.game.mods

import org.elkoserver.feature.basicexamples.cartesian.CartesianPosition
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.JSONLiteralFactory.targetVerb
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.ContextMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.Msg
import org.elkoserver.server.context.User

/**
 * A simple context mod to enable users in a context to move around.
 */
class Movement @JSONMethod("minx", "miny", "maxx", "maxy") constructor(
        minX: OptInteger,
        minY: OptInteger,
        maxX: OptInteger,
        maxY: OptInteger) : Mod(), ContextMod {
    private val myMinX: Int = minX.value(-100)
    private val myMinY: Int = minY.value(-100)
    private val myMaxX: Int = maxX.value(100)
    private val myMaxY: Int = maxY.value(100)

    override fun encode(control: EncodeControl) =
            if (control.toClient()) {
                null
            } else {
                JSONLiteralFactory.type("movement", control).apply {
                    addParameter("minx", myMinX)
                    addParameter("miny", myMinY)
                    addParameter("maxx", myMaxX)
                    addParameter("maxy", myMaxY)
                    finish()
                }
            }

    @JSONMethod("x", "y")
    fun move(from: User, x: Int, y: Int) {
        ensureSameContext(from)
        if (x < myMinX || myMaxX < x || y < myMinY || myMaxY < y) {
            from.send(Msg.msgError(`object`(), "move", "movement out of bounds"))
        } else {
            val pos = from.getMod(CartesianPosition::class.java)
                    ?: throw MessageHandlerException("user $from attempted move on $this but Cartesian position mod not present")
            pos.set(x, y)
            context().send(msgMove(from, x, y, null))
        }
    }

    companion object {
        @JvmStatic
        fun msgMove(who: Referenceable?, x: Int, y: Int, into: Referenceable?) =
                targetVerb(who, "move").apply {
                    addParameter("x", x)
                    addParameter("y", y)
                    addParameterOpt("into", into)
                    finish()
                }
    }
}
