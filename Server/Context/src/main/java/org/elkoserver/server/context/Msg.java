package org.elkoserver.server.context;

import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;

import static org.elkoserver.json.JSONLiteralFactory.targetVerb;

/**
 * Utility class consisting of static methods that generate various generally
 * useful messages that can be sent to the client.  These messages are sent by
 * a number of different classes, including, potentially, application-defined
 * {@link Mod} classes, so the methods to construct these messages don't
 * naturally belong with any particular server abstraction.  Hence this bag of
 * miscellany.
 */
public class Msg {
    /**
     * Suppress the Miranda constructor.
     */
    private Msg() { }

    /**
     * Create a 'delete' message.  This directs a client to delete an object.
     *
     * @param target  Object the message is being sent to (the object being
     *    deleted).
     */
    public static JSONLiteral msgDelete(Referenceable target) {
        JSONLiteral msg = targetVerb(target, "delete");
        msg.finish();
        return msg;
    }
    
    /**
     * Create an 'error' message.  This informs the client that something went
     * wrong.
     *
     * @param target  Object the message is being sent to (the object being
     *    informed).
     * @param op  Operation to be performed.
     * @param error  Contents of the error message.
     */
    public static JSONLiteral msgError(Referenceable target, String op,
                                       String error)
    {
        JSONLiteral msg = targetVerb(target, op);
        msg.addParameter("error", error);
        msg.finish();
        return msg;
    }

    /**
     * Create an 'exit' message.
     *
     * @param target  Object the message is being sent to.
     * @param why  Helpful text explaining the reason for the exit.
     * @param whyCode  Machine readable tag indicating the reason for the exit.
     * @param reload  True if client should attempt a reload.
     */
    static JSONLiteral msgExit(Referenceable target, String why,
                               String whyCode, boolean reload)
    {
        JSONLiteral msg = targetVerb(target, "exit");
        msg.addParameterOpt("why", why);
        msg.addParameterOpt("whycode", whyCode);
        if (reload) {
            msg.addParameter("reload", reload);
        }
        msg.finish();
        return msg;
    }

    /**
     * Create a 'make' message.  This directs a client to create an object.
     *
     * @param target  Object the message is being sent to (the object that is
     *    to be the container of the new object).
     * @param obj  The object that is to be created by the client.
     * @param maker  The user who is to be represented as the creator of the
     *    object, or null if none is.
     * @param you  If true, object being made is its recipient.
     * @param sess  The client context session ID, or null if there is none.
     */
    public static JSONLiteral msgMake(Referenceable target, BasicObject obj,
                                      User maker, boolean you, String sess)
    {
        JSONLiteral msg = targetVerb(target, "make");
        msg.addParameter("obj", (Encodable) obj);
        msg.addParameterOpt("maker", (Referenceable) maker);
        if (you) {
            msg.addParameter("you", you);
        }
        msg.addParameterOpt("sess", sess);
        msg.finish();
        return msg;
    }

    /**
     * Create a 'make' message.  This directs a client to create an object.
     * This method is a notational convenience; it is equivalent to the
     * five-argument 'make' with the 'you' parameter set to false (which is, by
     * far, the typical case) and no context session ID.
     *
     * @param target  Object the message is being sent to (the object that is
     *    to be the container of the new object).
     * @param obj  The object that is to be created by the client.
     * @param maker  The user who is to be represented as the creator of the
     *    object, or null if none is.
     */
    public static JSONLiteral msgMake(Referenceable target, BasicObject obj,
                                      User maker)
    {
        return msgMake(target, obj, maker, false, null);
    }

    /**
     * Create a 'make' message with a default creator and explicit session
     * identifier.  This method is exactly equivalent to:
     *
     * <p><code>msgMake(target, obj, null, false, sess)</code>
     *
     * <p>and is provided just for convenience.
     *
     * @param target  Object the message is being sent to (the object that is
     *    to be the container of the new object).
     * @param obj  The object that is to be created by the client.
     * @param sess  The client context session ID, or null if there is none.
     */
    public static JSONLiteral msgMake(Referenceable target, BasicObject obj,
                                      String sess)
    {
        return msgMake(target, obj, null, false, sess);
    }

    /**
     * Create a 'make' message with a default creator.  This method is
     * exactly equivalent to:
     *
     * <p><code>msgMake(target, obj, null, false, null)</code>
     *
     * <p>and is provided just for convenience.
     *
     * @param target  Object the message is being sent to (the object that is
     *    to be the container of the new object).
     * @param obj  The object that is to be created by the client.
     */
    public static JSONLiteral msgMake(Referenceable target, BasicObject obj) {
        return msgMake(target, obj, null, false, null);
    }

    /**
     * Create a 'push' message.  This directs a client to push the browser to a
     * different URL than the one it is looking at.
     *
     * @param target  Object the message is being sent to (normally this will
     *    be a user or context).
     * @param from  Object the message is to be alleged to be from, or
     *    null if not relevant.  This normally indicates the user who is doing
     *    the pushing.
     * @param url  The URL being pushed.
     * @param frame  Name of a frame to push the URL into, or null if not
     *    relevant.
     * @param features  Features string to associate with the URL, or null if
     *    not relevant.
     */
    public static JSONLiteral msgPush(Referenceable target, Referenceable from,
                                     String url, String frame, String features)
    {
        JSONLiteral msg = targetVerb(target, "push");
        msg.addParameterOpt("from", from);
        msg.addParameter("url", url);
        msg.addParameterOpt("frame", frame);
        msg.addParameterOpt("features", features);
        msg.finish();
        return msg;
    }

    /**
     * Create a 'ready' message.
     *
     * @param target  Object the message is being sent to.
     */
    static JSONLiteral msgReady(Referenceable target) {
        JSONLiteral msg = targetVerb(target, "ready");
        msg.finish();
        return msg;
    }

    /**
     * Create a 'say' message.  This directs a client to display chat text.
     *
     * @param target  Object the message is being sent to (normally this will
     *    be a user or context).
     * @param from  Object the message is to be alleged to be from, or null if
     *    not relevant.  This normally indicates the user who is speaking.
     * @param text  The text to be said.
     */
    public static JSONLiteral msgSay(Referenceable target, Referenceable from,
                                     String text)
    {
        JSONLiteral msg = targetVerb(target, "say");
        msg.addParameterOpt("from", from);
        msg.addParameter("text", text);
        msg.finish();
        return msg;
    }
}
