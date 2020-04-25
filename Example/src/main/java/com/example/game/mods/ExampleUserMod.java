package com.example.game.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.User;
import org.elkoserver.server.context.UserMod;

import static org.elkoserver.json.JSONLiteralFactory.targetVerb;
import static org.elkoserver.json.JSONLiteralFactory.type;

/**
 * An empty user mod, to get you started.
 */
public class ExampleUserMod extends Mod implements UserMod {

    @JSONMethod
    public ExampleUserMod() {
    }

    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = type("exu", control);
        result.finish();
        return result;
    }

    @JSONMethod({ "arg", "otherarg" })
    public void userverb(User from, String arg, OptString otherArg)
        throws MessageHandlerException
    {
        ensureSameUser(from);
        JSONLiteral response = msgUserVerb(from, arg, otherArg.value(null));
        from.send(response);
    }

    private static JSONLiteral msgUserVerb(Referenceable target, String arg,
                                           String otherArg)
    {
        JSONLiteral msg = targetVerb(target, "userverb");
        msg.addParameter("arg", arg);
        msg.addParameterOpt("otherarg", otherArg);
        msg.finish();
        return msg;
    }
}
