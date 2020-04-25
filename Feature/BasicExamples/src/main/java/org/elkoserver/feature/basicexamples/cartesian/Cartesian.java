package org.elkoserver.feature.basicexamples.cartesian;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.server.context.*;

/**
 * Mod to provide an item with 2D (rectangular) geometry.  This mod may only be
 * attached to an item, not to a context or a user.
 *
 * This mod keeps track of a position and a rectangular extent (e.g., a width
 * and a height).  Position is reckoned relative to the object's container.
 * Dimensions are specified by integers whose unit interpretation (e.g.,
 * pixels, inches, furlongs, attoparsecs, etc.) is left to the application.
 */
public class Cartesian extends Mod implements ItemMod {
    /* Persistent state, initialized from database. */

    /** Location X coordinate, in pixels. */
    private int myLeft;

    /** Location Y coordinate, in pixels. */
    private int myTop;

    /** Width, in pixels. */
    private int myWidth;

    /** Height, in pixels. */
    private int myHeight;

    /**
     * JSON-driven constructor.
     *
     * @param width  Horizontal extent of the geometry.
     * @param height  Vertical extent of the geometry.
     * @param left  X coordinate of object position relative to container.
     * @param top  Y coordinate of object position relative to container.
     */
    @JSONMethod({ "width", "height", "left", "top" })
    public Cartesian(int width, int height, int left, int top) {
        myWidth = width;
        myHeight = height;
        myLeft = left;
        myTop = top;
    }

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this mod.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("cart", control);
        result.addParameter("width", myWidth);
        result.addParameter("height", myHeight);
        result.addParameter("left", myLeft);
        result.addParameter("top", myTop);
        result.finish();
        return result;
    }

    /**
     * Message handler for the 'move' message.
     *
     * <p>This message is a request from a client to move this object to a
     * different location and/or container.  If the move is successful, a
     * corresponding 'move' message is broadcast to the context.<p>
     *
     * <u>recv</u>: <code> { to:<i>REF</i>, op:"move", into:<i>optREF</i>,
     *                     left:<i>INT</i>, top:<i>INT</i> } </code><br>
     *
     * <u>send</u>: <code> { to:<i>REF</i>, op:"move", into:<i>optREF</i>,
     *                     left:<i>INT</i>, top:<i>INT</i> } </code>
     *
     * @param from  The user who sent the message.
     * @param into  Container into which object should be placed (optional,
     *    defaults to same container, i.e., to leaving the container
     *    unchanged).
     * @param left  X coordinate of new position relative to container.
     * @param top  Y coordinate of new position relative to container.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     *    this mod or if the proposed destination container is invalid.
     */
    @JSONMethod({ "into", "left", "top" })
    public void move(User from, OptString into, int left, int top)
        throws MessageHandlerException
    {
        ensureSameContext(from);
        Item item = (Item) object();
        BasicObject newContainer = null;
        String newContainerRef = into.value(null);
        if (newContainerRef != null) {
            newContainer = context().get(newContainerRef);
            if (!ContainerValidity.validContainer(newContainer, from)) {
                throw new MessageHandlerException(
                    "invalid move destination container " + newContainerRef);
            }
            item.setContainer(newContainer);
        }
        myLeft = left;
        myTop = top;
        markAsChanged();
        context().send(msgMove(item, newContainer, left, top));
    }

    /**
     * Create a 'move' message.
     *
     * @param target  Object the message is being sent to.
     * @param into  Container object is to be placed into (null if container is
     *    not to be changed).
     * @param left  X coordinate of new position relative to container.
     * @param top  Y coordinate of new position relative to container.
     */
    private static JSONLiteral msgMove(Referenceable target, BasicObject into,
                                       int left, int top)
    {
        JSONLiteral msg = new JSONLiteral(target, "move");
        msg.addParameterOpt("into", (Referenceable) into);
        msg.addParameter("left", left);
        msg.addParameter("top", top);
        msg.finish();
        return msg;
    }
}