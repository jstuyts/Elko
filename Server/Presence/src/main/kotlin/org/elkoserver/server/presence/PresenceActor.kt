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
    private var label: String? = null

    /** Client object if this actor is a client, else null.  */
    internal var client: Client? = null
        private set

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
    override fun doAuth(handler: BasicProtocolHandler, auth: AuthDesc?, newLabel: String): Boolean {
        var success = false
        if (myFactory.verifyAuthorization(auth)) {
            if (handler is AdminHandler) {
                if (!amAdmin && myFactory.amAllowAdmin) {
                    amAdmin = true
                    label = newLabel
                    success = true
                }
            } else if (handler is ClientHandler) {
                if (client == null && myFactory.allowClient()) {
                    client = Client(myPresenceServer, this)
                    label = newLabel
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
            client?.doDisconnect()
            myPresenceServer.removeActor(this)
            amLoggedOut = true
            close()
        }
    }

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
        } else if (client == null) {
            doDisconnect()
            throw MessageHandlerException("actor $this attempted client operation without authorization")
        }
    }

    /**
     * @return a printable representation of this actor.
     */
    override fun toString() = label ?: super.toString()

    init {
        myPresenceServer.addActor(this)
    }
}
