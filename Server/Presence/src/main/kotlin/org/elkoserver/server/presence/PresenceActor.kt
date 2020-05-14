package org.elkoserver.server.presence

import org.elkoserver.foundation.actor.BasicProtocolActor
import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.actor.RoutingActor
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Actor for a connection to a presence server.  An actor may be associated
 * with either or both of the two service protocols offered by a presence
 * server ('admin' and 'presence'), according to the permissions granted by the
 * factory.
 *
 * @param connection  The connection for talking to this actor.
 * @param myFactory  The factory that created this actor.
 */
internal class PresenceActor(connection: Connection, private val myFactory: PresenceActorFactory,
                             private val gorgel: Gorgel, traceFactory: TraceFactory) : RoutingActor(connection, myFactory.refTable(), traceFactory), BasicProtocolActor {
    /** The presence server itself. */
    private val myPresenceServer: PresenceServer = myFactory.myPresenceServer

    /** True if actor has been disconnected.  */
    private var amLoggedOut = false

    /** Label for logging and such.  */
    private var myLabel: String? = null

    /** Client object if this actor is a client, else null.  */
    private var myClient: Client? = null

    /** True if actor is authorized to perform admin operations.  */
    private var amAdmin = false

    /**
     * Handle loss of connection from the user.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        doDisconnect()
        gorgel.i?.run { info("${this@PresenceActor} connection died: $connection") }
    }

    /**
     * Do the actual work of authorizing an actor.
     */
    override fun doAuth(handler: BasicProtocolHandler, auth: AuthDesc?, label: String): Boolean {
        var success = false
        if (myFactory.verifyAuthorization(auth)) {
            if (handler is AdminHandler) {
                if (!amAdmin && myFactory.amAllowAdmin) {
                    amAdmin = true
                    myLabel = label
                    success = true
                }
            } else if (handler is ClientHandler) {
                if (myClient == null && myFactory.allowClient()) {
                    myClient = Client(myPresenceServer, this)
                    myLabel = label
                    success = true
                }
            }
        }
        return success
    }

    /**
     * Do the actual work of disconnecting an actor.
     */
    override fun doDisconnect() {
        if (!amLoggedOut) {
            gorgel.i?.run { info("disconnecting ${this@PresenceActor}") }
            myClient?.doDisconnect()
            myPresenceServer.removeActor(this)
            amLoggedOut = true
            close()
        }
    }

    /**
     * Get this actor's client facet.
     *
     * @return the Client object associated with this actor, or null if this
     * actor isn't a client.
     */
    fun client() = myClient

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do admin operations.
     */
    fun ensureAuthorizedAdmin() {
        if (amLoggedOut) {
            throw MessageHandlerException("actor $this attempted admin operation after logout")
        } else if (!amAdmin) {
            doDisconnect()
            throw MessageHandlerException("actor $this attempted admin operation without authorization")
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do client operations.
     */
    fun ensureAuthorizedClient() {
        if (amLoggedOut) {
            throw MessageHandlerException("actor $this attempted client operation after logout")
        } else if (myClient == null) {
            doDisconnect()
            throw MessageHandlerException("actor $this attempted client operation without authorization")
        }
    }

    /**
     * Return this actor's label.
     */
    fun label() = myLabel

    /**
     * @return a printable representation of this actor.
     */
    override fun toString() = myLabel ?: super.toString()

    init {
        myPresenceServer.addActor(this)
    }
}
