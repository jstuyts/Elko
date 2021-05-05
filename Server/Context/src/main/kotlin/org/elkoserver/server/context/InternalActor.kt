package org.elkoserver.server.context

import org.elkoserver.foundation.actor.BasicProtocolActor
import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.actor.RoutingActor
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Actor for an internal connection to a context server from within the server
 * farm.  Such connections may send messages to any addressable object but do
 * not have associated users and are not placed into any context.
 *
 * @param connection  The connection for talking to this actor.
 * @param myFactory  Factory of the listener that accepted the connection.
 */
class InternalActor internal constructor(
        connection: Connection,
        private val myFactory: InternalActorFactory,
        private val gorgel: Gorgel,
        commGorgel: Gorgel,
        mustSendDebugReplies: Boolean) : RoutingActor(connection, myFactory.contextor.refTable, commGorgel, mustSendDebugReplies), BasicProtocolActor {

    /** Flag that connection has been authorized.  */
    private var amAuthorized = false

    /** Optional convenience label for logging and such.  */
    private var label: String? = null

    /**
     * Handle loss of connection from the actor.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        doDisconnect()
        gorgel.i?.run { info("${this@InternalActor} connection died: $connection $reason") }
    }

    /**
     * Do the actual work of authorizing an actor.
     */
    override fun doAuth(handler: BasicProtocolHandler, auth: AuthDesc?, newLabel: String): Boolean {
        label = newLabel
        amAuthorized = myFactory.verifyInternalAuthorization(auth)
        return amAuthorized
    }

    /**
     * Do the actual work of disconnecting an actor.
     */
    override fun doDisconnect() {
        gorgel.i?.run { info("disconnecting ${this@InternalActor}") }
        close()
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do it.
     */
    fun ensureAuthorized() {
        if (!amAuthorized) {
            throw MessageHandlerException("internal connection $this attempted operation without authorization")
        }
    }

    /**
     * @return a printable representation of this actor.
     */
    override fun toString(): String = label ?: super.toString()
}
