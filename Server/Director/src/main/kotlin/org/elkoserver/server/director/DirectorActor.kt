package org.elkoserver.server.director

import org.elkoserver.foundation.actor.BasicProtocolActor
import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.actor.RoutingActor
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.ordinalgeneration.OrdinalGenerator
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Actor for a connection to a director.  An actor may be associated with any
 * or all of the three service protocols offered by a director ('admin',
 * 'provider', and 'user'), according to the permissions granted by the
 * factory.
 *
 * @param connection  The connection for talking to this actor.
 * @param myFactory  Factory of the listener that accepted the connection.
 */
internal class DirectorActor(
        connection: Connection,
        private val myFactory: DirectorActorFactory,
        private val gorgel: Gorgel,
        private val providerGorgel: Gorgel,
        traceFactory: TraceFactory,
        private val ordinalGenerator: OrdinalGenerator,
        mustSendDebugReplies: Boolean) : RoutingActor(connection, myFactory.refTable(), traceFactory, mustSendDebugReplies), BasicProtocolActor {
    private val myDirector = myFactory.director

    /** True if actor has been disconnected.  */
    private var amLoggedOut = false

    /** Optional convenience label for logging and such.  */
    internal var label: String? = null
        private set

    /** Admin object if this actor is an admin, else null.  */
    internal var admin: Admin? = null
        private set

    /** Provider object if this actor is a provider, else null.  */
    internal var provider: Provider? = null
        private set

    /** True if this actor is a user.  */
    private var amUser = false

    /**
     * Handle loss of connection from the user.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        doDisconnect()
        gorgel.i?.run { info("${this@DirectorActor} connection died: $connection") }
    }

    /**
     * Do the actual work of authorizing an actor.
     */
    override fun doAuth(handler: BasicProtocolHandler, auth: AuthDesc?, newLabel: String): Boolean {
        label = newLabel
        var success = false
        if (myFactory.verifyAuthorization(auth)) {
            if (handler is AdminHandler) {
                if (admin == null && myFactory.allowAdmin) {
                    admin = Admin(myDirector, this)
                    success = true
                } else {
                    gorgel.warn("auth failed: admin access not allowed")
                }
            } else if (handler is ProviderHandler) {
                if (provider == null && myFactory.allowProvider() &&
                        !myDirector.isFull) {
                    provider = Provider(myDirector, this, providerGorgel, ordinalGenerator)
                    success = true
                } else {
                    gorgel.warn("auth failed: provider access not allowed")
                }
            } else if (handler is UserHandler) {
                if (!amUser && myFactory.allowUser) {
                    amUser = true
                    success = true
                } else {
                    gorgel.warn("auth failed: user access not allowed")
                }
            } else {
                gorgel.warn("auth failed: unknown handler type")
            }
        } else {
            gorgel.warn("auth failed: credential verification failure")
        }
        return success
    }

    /**
     * Do the actual work of disconnecting an actor.
     */
    override fun doDisconnect() {
        if (!amLoggedOut) {
            gorgel.i?.run { info("disconnecting ${this@DirectorActor}") }
            provider?.doDisconnect()
            admin?.doDisconnect()
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
        } else if (admin == null) {
            doDisconnect()
            throw MessageHandlerException("actor $this attempted admin operation without authorization")
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do provider operations.
     */
    fun ensureAuthorizedProvider() {
        if (amLoggedOut) {
            throw MessageHandlerException("actor $this attempted provider operation after logout")
        } else if (provider == null) {
            doDisconnect()
            throw MessageHandlerException("actor $this attempted provider operation without authorization")
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do user operations.
     */
    fun ensureAuthorizedUser() {
        if (amLoggedOut) {
            throw MessageHandlerException("actor $this attempted user operation after logout")
        } else if (!amUser && provider == null && admin == null) {
            doDisconnect()
            throw MessageHandlerException("actor $this attempted user operation without authorization")
        }
    }

    /**
     * Test if this actor corresponds to a connection from within the
     * server farm, i.e., that it is not just a user.
     */
    val isInternal: Boolean
        get() = provider != null

    /**
     * @return a printable representation of this actor.
     */
    override fun toString() = label ?: super.toString()
}
