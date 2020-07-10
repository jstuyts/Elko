package org.elkoserver.foundation.actor

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.json.JsonLiteral

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
}
