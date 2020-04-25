package org.elkoserver.server.context.test;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.GeneralMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.User;

import static org.elkoserver.json.JSONLiteralFactory.targetVerb;
import static org.elkoserver.json.JSONLiteralFactory.type;

/**
 * Mod to enable an object to function as a simple counter, mainly for testing
 * control over persistence.
 */
public class Counter extends Mod implements GeneralMod {
    /** Current count. */
    private int myCount;

    /**
     * JSON-driven constructor.
     */
    @JSONMethod({ "count" })
    public Counter(int count) {
        myCount = count;
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
        JSONLiteral result = type("counter", control);
        result.addParameter("count", myCount);
        result.finish();
        return result;
    }

    /**
     * Message handler for the 'inc' message.
     *
     * This message requests the counter to increment.
     *
     * <u>recv</u>: <code> { to:<i>REF</i>, op:"inc" } </code><br>
     *
     * <u>send</u>: <code> { to:<i>REF</i>, op:"set",
     *                     from:<i>REF</i>, count:<i>int</i> } </code>
     *
     * @param from  The user requesting the census.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     *    this mod, or if this mod is attached to a user and 'from' is not that
     *    user.
     */
    @JSONMethod
    public void inc(User from) throws MessageHandlerException {
        ensureSameContext(from);
        ++myCount;
        markAsChanged();
        JSONLiteral announce = targetVerb(object(), "set");
        announce.addParameter("count", myCount);
        announce.finish();
        context().send(announce);
    }
}
