package org.elkoserver.foundation.actor

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory.targetVerb
import org.elkoserver.json.Referenceable

/**
 * An object representing some entity interacting with this server (whatever
 * server this is) over the net.  It sits on top of a Connection.  It is both a
 * Deliverer that delivers outgoing messages over the connection and a message
 * acceptor that receives and dispatches incoming messages arriving on the
 * connection.
 *
 * @param myConnection  Connection to communicate with the entity at the
 *    other end.
 */
abstract class Actor internal constructor(private val myConnection: Connection, protected val mustSendDebugReplies: Boolean) : Deliverer, MessageHandler {

    /**
     * Close this Actor's connection.
     */
    fun close() {
        myConnection.close()
    }

    /**
     * Send a message over this Actor's connection to the entity at the other
     * end.
     *
     * @param message  The message to send.
     */
    override fun send(message: JsonLiteral) {
        myConnection.sendMsg(message)
    }

    companion object {
        /**
         * Create an 'auth' message.
         *
         * @param target  Object the message is being sent to.
         * @param auth  Authentication information to use.
         * @param label  Label to identify the entity seeking authorization.
         */
        fun msgAuth(target: Referenceable, auth: AuthDesc?, label: String?) = msgAuth(target.ref(), auth, label)

        /**
         * Create an 'auth' message.
         *
         * @param target  Object the message is being sent to.
         * @param auth  Authentication information to use.
         * @param label  Label to identify the entity seeking authorization.
         */
        fun msgAuth(target: String, auth: AuthDesc?, label: String?) =
                targetVerb(target, "auth").apply {
                    addParameterOpt("auth", auth)
                    addParameterOpt("label", label)
                    finish()
                }
    }
}
