package org.elkoserver.foundation.actor

import org.elkoserver.foundation.json.DispatchTarget
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.net.Connection
import org.elkoserver.json.JsonLiteralFactory.targetVerb
import org.elkoserver.json.JsonObject
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * An [Actor] that receives targeted JSON messages over its connection.
 *
 *
 * This class is abstract, in that its implementation of the [org.elkoserver.foundation.net.MessageHandler] interface is
 * incomplete: it implements the [org.elkoserver.foundation.net.MessageHandler.processMessage]
 * method, but subclasses must implement [org.elkoserver.foundation.net.MessageHandler.connectionDied]
 * method (as well as any [org.elkoserver.foundation.json.JsonMethod] methods for whatever specific
 * object behavior the subclass is intended for).
 *
 * @param connection  Connection associated with this actor.
 * @param myRefTable  Table for object ref decoding and message dispatch.
 */
abstract class RoutingActor protected constructor(
        connection: Connection,
        private val myRefTable: RefTable,
        private val commGorgel: Gorgel,
        mustSendDebugReplies: Boolean) : Actor(connection, mustSendDebugReplies), DispatchTarget {

    /**
     * Send a 'debug' message over the connection, addressed to the 'error'
     * object.
     *
     * @param errorText  Error message text to send in the parameter 'msg'.
     */
    private fun debugMsg(errorText: String) {
        val msg = targetVerb("error", "debug").apply {
            addParameter("msg", errorText)
            finish()
        }
        send(msg)
    }

    /**
     * Process a received message by dispatching it to the object that the
     * message addresses as its target, according to the [RefTable] that
     * was provided in this actor's constructor.
     *
     * @param connection  Connection over which the message was received.
     * @param receivedMessage  The message received.  Normally this should be a
     * [JsonObject], but it could be a [Throwable] indicating a
     * problem receiving or parsing the message.
     */
    override fun processMessage(connection: Connection, receivedMessage: Any) {
        var problem: Throwable? = null
        if (receivedMessage is JsonObject) {
            try {
                myRefTable.dispatchMessage(this, receivedMessage)
            } catch (result: MessageHandlerException) {
                problem = result.cause
                if (problem == null) {
                    problem = result
                } else {
                    commGorgel.error("exception in message handler", problem)
                }
            }
        } else if (receivedMessage is Throwable) {
            problem = receivedMessage
        }
        if (problem != null) {
            val warning = "error handling message: $problem"
            commGorgel.warn(warning)
            if (mustSendDebugReplies) {
                debugMsg(warning)
            }
        }
    }
}
