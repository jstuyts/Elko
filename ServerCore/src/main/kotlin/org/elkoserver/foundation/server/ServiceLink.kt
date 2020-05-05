package org.elkoserver.foundation.server

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.json.JSONLiteral
import java.util.LinkedList

/**
 * Class representing a connection to a service rather than to a specific
 * client or server.  A service link can be maintained across loss of
 * connectivity to specific connected entities, at the cost of allowing some
 * of the state associated with the connection be ephemeral.
 *
 * @param myService  The name of the service this link will connect to.
 * @param myServer  The server this link is working for.
 */
class ServiceLink internal constructor(private val myService: String, private val myServer: Server) : Deliverer {
    /** The actor this link uses to communicate with its service, or null if
     * the connection is currently not up.  */
    private var myActor: ServiceActor? = null

    /** A list of messages awaiting transmission, accumulated when the
     * connection is down.  */
    private var myPendingMessages: LinkedList<JSONLiteral>? = null

    /** Flag indicating that service connection setup failed.  */
    private var amFailed = false

    /**
     * Obtain the actor this link is currently using for message sends.
     *
     * @return this link's associated actor.
     */
    fun actor() = myActor

    /**
     * Take note that the actor this link was dependent on lost its connection.
     * Begin re-establishing the connection, and meanwhile start queuing
     * messages.
     */
    fun actorDied() {
        myActor = null
        if (!amFailed) {
            myServer.reestablishServiceConnection(myService, this)
        }
    }

    /**
     * Establish this link's connection (or re-establish it after it has been
     * lost).  Any messages that have been queued up in the interim will be
     * sent.
     *
     * @param actor  The new actor to use.
     */
    fun connectActor(actor: ServiceActor?) {
        myActor = actor
        while (myPendingMessages != null) {
            val message = myPendingMessages!!.peek()
            myActor!!.send(message)
            if (myActor == null) {
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
            myServer.trace().errorm(this.toString() +
                    " failed with pending outbound messages")
        }
    }

    /**
     * Send a message over this link to the entity at the other end.
     *
     * @param message  The message to send.
     */
    override fun send(message: JSONLiteral) {
        if (amFailed) {
            throw RuntimeException("message send on failed $this")
        }
        if (myActor == null) {
            if (myPendingMessages == null) {
                myPendingMessages = LinkedList()
            }
            myPendingMessages!!.addLast(message)
        } else {
            myActor!!.send(message)
        }
    }

    /**
     * Obtain the name of the service that this link connects to.
     *
     * @return the service name associated with this link.
     */
    fun service(): String = myService

    override fun toString() = "ServiceLink to $myService"
}
