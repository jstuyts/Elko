package org.elkoserver.server.broker

import org.elkoserver.foundation.actor.BasicProtocolActor
import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.actor.RoutingActor
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.ordinalgeneration.LongOrdinalGenerator
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Actor for a connection to a broker.  An actor may be associated with either
 * or both of the two service protocols offered by a broker ('admin' and
 * 'client'), according to the permissions granted by the factory.
 *
 * @param connection  The connection for talking to this actor.
 * @param myFactory  The factory that created this actor.
 */
internal class BrokerActor(
        connection: Connection,
        private val myFactory: BrokerActorFactory,
        private val gorgel: Gorgel,
        commGorgel: Gorgel,
        private val clientOrdinalGenerator: LongOrdinalGenerator,
        mustSendDebugReplies: Boolean) : RoutingActor(connection, myFactory.refTable(), commGorgel, mustSendDebugReplies), BasicProtocolActor {

    /** The broker itself.  */
    private val myBroker: Broker = myFactory.broker

    /** True if actor has been disconnected.  */
    private var amLoggedOut = false

    /** Label for logging and such.  */
    internal var label: String? = null

    /** Client object if this actor is a client, else null.  */
    internal var client: Client? = null

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
        gorgel.i?.run { info("${this@BrokerActor} connection died: $connection") }
    }

    /**
     * Do the actual work of authorizing an actor.
     */
    override fun doAuth(handler: BasicProtocolHandler, auth: AuthDesc?, newLabel: String): Boolean {
        var success = false
        if (myFactory.verifyAuthorization(auth)) {
            if (handler is AdminHandler) {
                if (!amAdmin && myFactory.allowAdmin) {
                    amAdmin = true
                    label = newLabel
                    success = true
                }
            } else if (handler is ClientHandler) {
                if (client == null && myFactory.allowClient) {
                    client = Client(myBroker, this, clientOrdinalGenerator)
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
            gorgel.i?.run { info("disconnecting ${this@BrokerActor}") }
            client?.doDisconnect()
            if (amAdmin) {
                myBroker.unwatchServices(this)
                myBroker.unwatchLoad(this)
            }
            myBroker.removeActor(this)
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
        myBroker.addActor(this)
    }
}
