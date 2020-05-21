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
 * @param x  X-coordinate
 * @param y Y-coordinate
 */
class CartesianPosition @JSONMethod("x", "y") constructor(var x: Int, var y: Int) : Mod(), UserMod, ItemMod {

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
                addParameter("x", x)
                addParameter("y", y)
                finish()
            }

    operator fun set(newX: Int, newY: Int) {
        x = newX
        y = newY
        markAsChanged()
    }
}
