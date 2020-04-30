package org.elkoserver.server.repository

import org.elkoserver.foundation.actor.BasicProtocolActor
import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.actor.RoutingActor
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory

/**
 * Actor for a connection to a repository.  An actor may be associated with
 * either or both of the two service protocols offered by a repository ('admin'
 * and 'rep'), according to the permissions granted by the factory.
 *
 * @param connection  The connection for talking to this actor.
 * @param myFactory  The factory that created this actor.
 * @param tr  Trace object for diagnostics.
 */
internal class RepositoryActor(connection: Connection?, private val myFactory: RepositoryActorFactory,
                               private val tr: Trace, traceFactory: TraceFactory?) : RoutingActor(connection, myFactory.refTable(), traceFactory), BasicProtocolActor {
    private val myRepository = myFactory.myRepository

    /** True if actor has been disconnected.  */
    private var amLoggedOut = false

    /** Optional convenience label for logging and such.  */
    private var myLabel: String? = null

    /** True if actor is authorized to perform admin operations.  */
    private var amAdmin = false

    /** True if actor is authorized to perform repository operations.  */
    private var amRep = false

    /**
     * Handle loss of connection from the actor.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        doDisconnect()
        tr.eventm("$this connection died: $connection$reason")
    }

    /**
     * Do the actual work of authorizing an actor.
     */
    override fun doAuth(handler: BasicProtocolHandler, auth: AuthDesc,
                        label: String): Boolean {
        myLabel = label
        var success = false
        if (myFactory.verifyAuthorization(auth)) {
            if (handler is AdminHandler) {
                if (myFactory.amAllowAdmin) {
                    amAdmin = true
                    success = true
                }
            } else if (handler is RepHandler) {
                if (myFactory.allowRep()) {
                    amRep = true
                    success = true
                    myRepository.countRepClients(1)
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
            tr.eventm("disconnecting $this")
            if (amRep) {
                myRepository.countRepClients(-1)
            }
            amLoggedOut = true
            close()
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do admin operations.
     *
     */
    fun ensureAuthorizedAdmin() {
        if (amLoggedOut) {
            throw MessageHandlerException("actor " + this +
                    " attempted admin operation after logout")
        } else if (!amAdmin) {
            doDisconnect()
            throw MessageHandlerException("actor " + this +
                    " attempted admin operation without authorization")
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do repository operations.
     *
     */
    fun ensureAuthorizedRep() {
        if (amLoggedOut) {
            throw MessageHandlerException("actor " + this +
                    " attempted repository operation after logout")
        } else if (!amRep) {
            doDisconnect()
            throw MessageHandlerException("actor " + this +
                    " attempted repository operation without authorization")
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
