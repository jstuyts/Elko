package org.elkoserver.server.context

import org.elkoserver.foundation.actor.BasicProtocolActor
import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.actor.RoutingActor
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory

/**
 * Actor for an internal connection to a context server from within the server
 * farm.  Such connnections may send messages to any addressable object but do
 * not have associated users and are not placed into any context.
 *
 * @param connection  The connection for talking to this actor.
 * @param myFactory  Factory of the listener that accepted the connection.
 * @param tr  Trace object for diagnostics.
 */
class InternalActor internal constructor(connection: Connection, private val myFactory: InternalActorFactory,
                                         private val tr: Trace, traceFactory: TraceFactory) : RoutingActor(connection, myFactory.contextor(), traceFactory), BasicProtocolActor {

    /** Flag that connection has been authorized.  */
    private var amAuthorized = false

    /** Optional convenience label for logging and such.  */
    private var myLabel: String? = null

    /**
     * Handle loss of connection from the actor.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        doDisconnect()
        tr.eventm("$this connection died: $connection $reason")
    }

    /**
     * Do the actual work of authorizing an actor.
     */
    override fun doAuth(handler: BasicProtocolHandler, auth: AuthDesc?, label: String): Boolean {
        myLabel = label
        amAuthorized = myFactory.verifyInternalAuthorization(auth)
        return amAuthorized
    }

    /**
     * Do the actual work of disconnecting an actor.
     */
    override fun doDisconnect() {
        tr.eventm("disconnecting $this")
        close()
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do it.
     */
    fun ensureAuthorized() {
        if (!amAuthorized) {
            throw MessageHandlerException("internal connection " + this +
                    " attempted operation without authorization")
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
}
