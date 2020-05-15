package org.elkoserver.server.workshop

import org.elkoserver.foundation.actor.BasicProtocolActor
import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.actor.RoutingActor
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Actor for a connection to a workshop.  An actor may be associated with
 * either or both of the two service protocols offered by a workshop (admin
 * and client), according to the permissions granted by the factory.
 *
 * @param connection  The connection for talking to this actor.
 * @param myFactory  The factory that created this actor.
 */
class WorkshopActor internal constructor(connection: Connection, private val myFactory: WorkshopActorFactory,
                                         private val gorgel: Gorgel, traceFactory: TraceFactory) : RoutingActor(connection, myFactory.workshop, traceFactory), BasicProtocolActor {

    /** True if actor has been disconnected.  */
    private var amLoggedOut = false

    /** Optional convenience label for logging and such.  */
    var label: String? = null

    /** True if actor is authorized to perform admin operations.  */
    private var amAdmin = false

    /** True if actor is authorized to perform workshop client operations.  */
    private var amClient = false

    /**
     * Handle loss of connection from the actor.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        doDisconnect()
        gorgel.i?.run { info("${this@WorkshopActor} connection died: $connection$reason") }
    }

    /**
     * Do the actual work of authorizing an actor.
     */
    override fun doAuth(handler: BasicProtocolHandler, auth: AuthDesc?, label: String): Boolean {
        this.label = label
        var success = false
        if (myFactory.verifyAuthorization(auth)) {
            if (handler is AdminHandler) {
                if (myFactory.allowAdmin) {
                    amAdmin = true
                    success = true
                }
            } else if (handler is ClientHandler) {
                if (myFactory.allowClient()) {
                    amClient = true
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
            gorgel.i?.run { info("disconnecting ${this@WorkshopActor}") }
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
     * actor who is authorized to do workshop client operations.
     *
     */
    fun ensureAuthorizedClient() {
        if (amLoggedOut) {
            throw MessageHandlerException("actor $this attempted client operation after logout")
        } else if (!amClient) {
            doDisconnect()
            throw MessageHandlerException("actor $this attempted client operation without authorization")
        }
    }

    /**
     * @return a printable representation of this actor.
     */
    override fun toString() = label ?: super.toString()
}
