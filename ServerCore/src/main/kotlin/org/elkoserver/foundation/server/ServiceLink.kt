package org.elkoserver.foundation.server

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.json.JsonLiteral
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.LinkedList

/**
 * Class representing a connection to a service rather than to a specific
 * client or server.  A service link can be maintained across loss of
 * connectivity to specific connected entities, at the cost of allowing some
 * of the state associated with the connection be ephemeral.
 *
 * @param service  The name of the service this link will connect to.
 * @param myServer  The server this link is working for.
 */
class ServiceLink internal constructor(internal val service: String, private val myServer: Server, private val gorgel: Gorgel) : Deliverer {
    /** The actor this link uses to communicate with its service, or null if
     * the connection is currently not up.  */
    private var actor: ServiceActor? = null

    /** A list of messages awaiting transmission, accumulated when the
     * connection is down.  */
    private var myPendingMessages: LinkedList<JsonLiteral>? = null

    /** Flag indicating that service connection setup failed.  */
    private var amFailed = false

    /**
     * Take note that the actor this link was dependent on lost its connection.
     * Begin re-establishing the connection, and meanwhile start queuing
     * messages.
     */
    fun actorDied() {
        actor = null
        if (!amFailed) {
            myServer.reestablishServiceConnection(service, this)
        }
    }

    /**
     * Establish this link's connection (or re-establish it after it has been
     * lost).  Any messages that have been queued up in the interim will be
     * sent.
     *
     * @param theActor  The new actor to use.
     */
    fun connectActor(theActor: ServiceActor?) {
        actor = theActor
        while (myPendingMessages != null) {
            val message = myPendingMessages!!.peek()
            actor!!.send(message)
            if (actor == null) {
                break
            }
            myPendingMessages!!.removeFirst()
            if (myPendingMessages!!.isEmpty()) {
                myPendingMessages = null
            }
        }
    }

    /**
     * Mark this service link as failed.
     */
    fun fail() {
        amFailed = true
        if (myPendingMessages != null && !myPendingMessages!!.isEmpty()) {
            gorgel.error("$this failed with pending outbound messages")
        }
    }

    /**
     * Send a message over this link to the entity at the other end.
     *
     * @param message  The message to send.
     */
    override fun send(message: JsonLiteral) {
        if (amFailed) {
            throw RuntimeException("message send on failed $this")
        }
        if (actor == null) {
            if (myPendingMessages == null) {
                myPendingMessages = LinkedList()
            }
            myPendingMessages!!.addLast(message)
        } else {
            actor!!.send(message)
        }
    }

    override fun toString(): String = "ServiceLink to $service"
}
