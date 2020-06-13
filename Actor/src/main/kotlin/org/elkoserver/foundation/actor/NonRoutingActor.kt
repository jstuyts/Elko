package org.elkoserver.foundation.actor

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.json.DispatchTarget
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.net.Connection
import org.elkoserver.json.JSONLiteralFactory.targetVerb
import org.elkoserver.json.JsonObject
import org.elkoserver.json.Referenceable
import org.elkoserver.util.trace.TraceFactory

/**
 * An [Actor] that receives untargeted JSON messages over its connection.
 *
 * This class is abstract, in that its implementation of the [org.elkoserver.foundation.net.MessageHandler] interface is
 * incomplete: it implements the [org.elkoserver.foundation.net.MessageHandler.processMessage]
 * method, but subclasses must implement the [org.elkoserver.foundation.net.MessageHandler.connectionDied]
 * method (as well as any [JSONMethod] methods for whatever specific
 * object behavior the subclass is intended for).
 *
 * In contrast to [RoutingActor], objects of this class disregard the
 * message targets of messages received, blindly assuming instead that all
 * messages received are for them.  This avoids setting up a lot of expensive
 * mechanism for a common special case, wherein a connection has a simple,
 * static message protocol with no object addressing.
 *
 * This class provides default implementations for the 'ping', 'pong', and
 * 'debug' messages, since objects of the variety that would use this class
 * should always support those particular messages anyway.
 *
 * @param connection  Connection associated with this actor.
 * @param myDispatcher  Dispatcher to invoke message handlers based on 'op'.
 */
abstract class NonRoutingActor protected constructor(
        connection: Connection,
        private val myDispatcher: MessageDispatcher,
        protected val traceFactory: TraceFactory,
        mustSendDebugReplies: Boolean) : Actor(connection, mustSendDebugReplies), Referenceable, DispatchTarget {

    /**
     * Send a 'debug' message over the connection, addressed to the 'error'
     * object.
     *
     * @param errorText  Error message text to send in the parameter 'msg'.
     */
    private fun debugMsg(errorText: String) {
        val msg = targetVerb(this, "debug").apply {
            addParameter("msg", errorText)
            finish()
        }
        send(msg)
    }

    /**
     * Process a received message by dispatching to this object directly using
     * the dispatcher that was provided in this actor's constructor.
     *
     * @param connection  Connection over which the message was received.
     * @param rawMessage  The message received.  Normally this should be a
     * [JsonObject], but it could be a [Throwable] indicating a
     * problem receiving or parsing the message.
     */
    override fun processMessage(connection: Connection, rawMessage: Any) {
        var report: Throwable? = null
        if (rawMessage is JsonObject) {
            try {
                myDispatcher.dispatchMessage(this, this, rawMessage)
            } catch (result: MessageHandlerException) {
                report = result.cause
                if (report == null) {
                    report = result
                } else {
                    traceFactory.comm.errorReportException(report,
                            "exception in message handler")
                }
            }
        } else if (rawMessage is Throwable) {
            report = rawMessage
        }
        if (report != null) {
            val warning = "message handler error: $report"
            traceFactory.comm.warningm(warning)
            if (mustSendDebugReplies) {
                debugMsg(warning)
            }
        }
    }

    /**
     * JSON method for the 'debug' message.
     *
     * This message delivers textual debugging information from the other end
     * of the connection.  The received text is written to the server log.
     *
     *
     *
     * <u>recv</u>: ` { to:*ignored*, op:"debug",
     * msg:*STR* } `<br></br>
     *
     * <u>send</u>: no reply is sent
     *
     * @param from  The connection over which the message was received.
     * @param msg  Text to write to the server log;
     */
    @JSONMethod("msg")
    fun debug(from: Deliverer, msg: String) {
        traceFactory.comm.eventi("Debug msg: $msg")
    }

    /**
     * JSON method for the 'ping' message.
     *
     * This message is a simple connectivity test.  Respond by sending a 'pong'
     * message back to the sender.
     *
     *
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
    @JSONMethod("tag")
    fun ping(from: Deliverer, tag: OptString) {
        from.send(msgPong(this, tag.value<String?>(null)))
    }

    /**
     * JSON method for the 'pong' message.
     *
     * This message is the reply to an earlier 'ping' message.  It is simply
     * discarded.
     *
     *
     *
     * <u>recv</u>: ` { to:*ignored*, op:"pong",
     * tag:*optSTR* } `<br></br>
     *
     * <u>send</u>: there is no reply sent
     *
     * @param from  The connection over which the message was received.
     * @param tag  Optional tag string, which should echo that (if any) from
     * the 'ping' message that caused this 'pong' to be sent.
     */
    @JSONMethod("tag")
    fun pong(from: Deliverer, tag: OptString) {
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
