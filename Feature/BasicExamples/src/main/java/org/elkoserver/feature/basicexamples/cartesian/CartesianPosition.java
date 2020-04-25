package org.elkoserver.feature.basicexamples.cartesian;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.ItemMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.UserMod;

import static org.elkoserver.json.JSONLiteralFactory.type;

/**
 * Position class representing an integer (x,y) coordinate on a plane.
 */
public class CartesianPosition extends Mod implements UserMod, ItemMod {
    private int myX;
    private int myY;

    /**
     * JSON-driven constructor.
     *
     * @param x  X-coordinate
     * @param y  Y-coordinate
     */
    @JSONMethod({ "x", "y" })
    public CartesianPosition(int x, int y) {
        myX = x;
        myY = y;
    }

    /**
     * Encode this position for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this position.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = type("cartpos", control);
        result.addParameter("x", myX);
        result.addParameter("y", myY);
        result.finish();
        return result;
    }

    /**
     * Obtain the X-coordinate of this position.
     *
     * @return this position's X-coordinate.
     */
    public int x() {
        return myX;
    }

    /**
     * Obtain the Y-coordinate of this position.
     *
     * @return this position's Y-coordinate.
     */
    public int y() {
        return myY;
    }

    public void set(int x, int y) {
        myX = x;
        myY = y;
        markAsChanged();
    }
}
