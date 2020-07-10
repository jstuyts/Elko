package org.elkoserver.server.context

import org.elkoserver.json.Encodable
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Utility class consisting of static methods that generate various generally
 * useful messages that can be sent to the client.  These messages are sent by
 * a number of different classes, including, potentially, application-defined
 * [Mod] classes, so the methods to construct these messages don't
 * naturally belong with any particular server abstraction.  Hence this bag of
 * miscellany.
 */
object Msg {
    /**
     * Create a 'delete' message.  This directs a client to delete an object.
     *
     * @param target  Object the message is being sent to (the object being
     * deleted).
     */
    fun msgDelete(target: Referenceable) =
            JsonLiteralFactory.targetVerb(target, "delete").apply {
                finish()
            }

    /**
     * Create an 'error' message.  This informs the client that something went
     * wrong.
     *
     * @param target  Object the message is being sent to (the object being
     * informed).
     * @param op  Operation to be performed.
     * @param error  Contents of the error message.
     */
    fun msgError(target: Referenceable, op: String, error: String?) =
            JsonLiteralFactory.targetVerb(target, op).apply {
                addParameter("error", error)
                finish()
            }

    /**
     * Create an 'exit' message.
     *
     * @param target  Object the message is being sent to.
     * @param why  Helpful text explaining the reason for the exit.
     * @param whyCode  Machine readable tag indicating the reason for the exit.
     * @param reload  True if client should attempt a reload.
     */
    fun msgExit(target: Referenceable, why: String?, whyCode: String?, reload: Boolean) =
            JsonLiteralFactory.targetVerb(target, "exit").apply {
                addParameterOpt("why", why)
                addParameterOpt("whycode", whyCode)
                if (reload) {
                    addParameter("reload", reload)
                }
                finish()
            }

    /**
     * Create a 'make' message.  This directs a client to create an object.
     *
     * @param target  Object the message is being sent to (the object that is
     * to be the container of the new object).
     * @param obj  The object that is to be created by the client.
     * @param maker  The user who is to be represented as the creator of the
     * object, or null if none is.
     * @param you  If true, object being made is its recipient.
     * @param sess  The client context session ID, or null if there is none.
     */
    fun msgMake(target: Referenceable, obj: BasicObject?, maker: User? = null, you: Boolean = false, sess: String? = null) =
            JsonLiteralFactory.targetVerb(target, "make").apply {
                addParameter("obj", obj as Encodable?)
                addParameterOpt("maker", maker as Referenceable?)
                if (you) {
                    addParameter("you", you)
                }
                addParameterOpt("sess", sess)
                finish()
            }

    /**
     * Create a 'make' message with a default creator and explicit session
     * identifier.  This method is exactly equivalent to:
     *
     * `msgMake(target, obj, null, false, sess)`
     *
     * and is provided just for convenience.
     *
     * @param target  Object the message is being sent to (the object that is
     * to be the container of the new object).
     * @param obj  The object that is to be created by the client.
     * @param sess  The client context session ID, or null if there is none.
     */
    fun msgMake(target: Referenceable, obj: BasicObject?, sess: String?) = msgMake(target, obj, null, false, sess)

    /**
     * Create a 'push' message.  This directs a client to push the browser to a
     * different URL than the one it is looking at.
     *
     * @param target  Object the message is being sent to (normally this will
     * be a user or context).
     * @param from  Object the message is to be alleged to be from, or
     * null if not relevant.  This normally indicates the user who is doing
     * the pushing.
     * @param url  The URL being pushed.
     * @param frame  Name of a frame to push the URL into, or null if not
     * relevant.
     * @param features  Features string to associate with the URL, or null if
     * not relevant.
     */
    fun msgPush(target: Referenceable, from: Referenceable, url: String?, frame: String?, features: String?) =
            JsonLiteralFactory.targetVerb(target, "push").apply {
                addParameterOpt("from", from)
                addParameter("url", url)
                addParameterOpt("frame", frame)
                addParameterOpt("features", features)
                finish()
            }

    /**
     * Create a 'ready' message.
     *
     * @param target  Object the message is being sent to.
     */
    fun msgReady(target: Referenceable) =
            JsonLiteralFactory.targetVerb(target, "ready").apply {
                finish()
            }

    /**
     * Create a 'say' message.  This directs a client to display chat text.
     *
     * @param target  Object the message is being sent to (normally this will
     * be a user or context).
     * @param from  Object the message is to be alleged to be from, or null if
     * not relevant.  This normally indicates the user who is speaking.
     * @param text  The text to be said.
     */
    fun msgSay(target: Referenceable, from: Referenceable?, text: String?) =
            JsonLiteralFactory.targetVerb(target, "say").apply {
                addParameterOpt("from", from)
                addParameter("text", text)
                finish()
            }
}
