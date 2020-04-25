package org.elkoserver.feature.basicexamples.cartesian

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.context.ItemMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.UserMod

/**
 * Position class representing an integer (x,y) coordinate on a plane.
 *
 * @param myX  X-coordinate
 * @param myY Y-coordinate
 */
class CartesianPosition @JSONMethod("x", "y") constructor(private var myX: Int, private var myY: Int) : Mod(), UserMod, ItemMod {

    /**
     * Encode this position for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this position.
     */
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("cartpos", control).apply {
                addParameter("x", myX)
                addParameter("y", myY)
                finish()
            }

    /**
     * Obtain the X-coordinate of this position.
     *
     * @return this position's X-coordinate.
     */
    fun x() = myX

    /**
     * Obtain the Y-coordinate of this position.
     *
     * @return this position's Y-coordinate.
     */
    fun y() = myY

    operator fun set(x: Int, y: Int) {
        myX = x
        myY = y
        markAsChanged()
    }
}
