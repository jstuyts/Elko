package org.elkoserver.foundation.actor

import org.elkoserver.foundation.json.BaseCommGorgelUsingObject
import org.elkoserver.foundation.json.DispatchTarget
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.json.JsonLiteralFactory.targetVerb
import org.elkoserver.json.Referenceable
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Utility message handler implementation base class that supports a basic JSON
 * protocol for connection housekeeping.  The supported protocol is common to
 * many actors in Elko.  It includes the messages 'auth', 'debug',
 * 'disconnect', 'ping', and 'pong'.  This base class provides default
 * implementations for these messages that should be satisfactory in nearly all
 * circumstances.
 */
abstract class BasicProtocolHandler protected constructor() : Referenceable, DispatchTarget, BaseCommGorgelUsingObject {
    private lateinit var commGorgel: Gorgel

    constructor(commGorgel: Gorgel) : this() {
        this.commGorgel = commGorgel
    }

    override fun setBaseCommGorgel(baseCommGorgel: Gorgel) {
        baseCommGorgel.getChild(this::class)
    }

    /**
     * JSON method for the 'auth' message.
     *
     * This message requests the server to authenticate the sender, according
     * to the type of user they want to become.  There is no reply, but if
     * authentication fails, the sender is disconnected.
     *
     * <u>recv</u>: ` { to:*REF*, op:"auth", auth:*AUTHDESC*,
     * label:*optSTR* } `<br></br>
     *
     * <u>send</u>: no reply is sent
     *
     * @param from  The connection over which the message was received.
     * @param auth  Authorization information being offered.
     * @param label  Descriptive label for this connection, for logging.
     */
    @JsonMethod("?auth", "label")
    fun auth(from: BasicProtocolActor, auth: AuthDesc?, label: OptString) {
        if (!from.doAuth(this, auth, label.value("<anonymous>"))) {
            from.doDisconnect()
        }
    }

    /**
     * JSON method for the 'debug' message.
     *
     * This message delivers textual debugging information from the other end
     * of the connection.  The received text is written to the server log.
     *
     * <u>recv</u>: ` { to:*ignored*, op:"debug",
     * msg:*STR* } `<br></br>
     *
     * <u>send</u>: no reply is sent
     *
     * @param from  The connection over which the message was received.
     * @param msg  Text to write to the server log;
     */
    @JsonMethod("msg")
    fun debug(from: BasicProtocolActor, msg: String) {
        commGorgel.i?.run { info("Debug msg: $msg") }
    }

    /**
     * JSON method for the 'disconnect' message.
     *
     * This message requests the server to close its connection to the
     * sender.
     *
     * <u>recv</u>: ` { to:*ignored*, op:"disconnect" } `<br></br>
     *
     * <u>send</u>: there is no reply, since the connection is closed
     *
     * @param from  The connection over which the message was received.
     */
    @JsonMethod
    fun disconnect(from: BasicProtocolActor) {
        from.doDisconnect()
    }

    /**
     * JSON method for the 'ping' message.
     *
     * This message is a simple connectivity test.  Responds by sending a
     * 'pong' message back to the sender.
     *
     * <u>recv</u>: ` { to:*REF*, op:"ping",
     * tag:*optSTR* } `<br></br>
     *
     * <u>send</u>: ` { to:*asReceived*, op:"pong",
     * tag:*asReceived* } `
     *
     * @param from  The connection over which the message was received.
     * @param tag  Optional tag string; if provided, it will be included in the
     * reply.
     */
    @JsonMethod("tag")
    fun ping(from: BasicProtocolActor, tag: OptString) {
        from.send(msgPong(this, tag.value<String?>(null)))
    }

    /**
     * JSON method for the 'pong' message.
     *
     * This message is the reply to an earlier 'ping' message.  It is simply
     * discarded.
     *
     * <u>recv</u>: ` { to:*ignored*, op:"pong",
     * tag:*optSTR* } `<br></br>
     *
     * <u>send</u>: no reply is sent
     *
     * @param from  The connection over which the message was received.
     * @param tag  Optional tag string, which should echo the tag (if any) from
     * the 'ping' message that caused this 'pong' to be sent.
     */
    @JsonMethod("tag")
    fun pong(from: BasicProtocolActor?, tag: OptString?) {
        /* Nothing to do here. */
    }

    companion object {
        /**
         * Generate a 'pong' message.
         *
         * @param target  Object the message is being sent to.
         * @param tag  Tag string (nominally from the 'ping' message that
         * triggered this) or null.
         */
        private fun msgPong(target: Referenceable, tag: String?) =
                targetVerb(target, "pong").apply {
                    addParameterOpt("tag", tag)
                    finish()
                }
    }
}
