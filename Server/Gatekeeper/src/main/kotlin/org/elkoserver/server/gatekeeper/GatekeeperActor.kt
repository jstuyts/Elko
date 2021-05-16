package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.actor.BasicProtocolActor
import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.actor.RoutingActor
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Actor representing a possibly multi-faceted connection to a gatekeeper.
 *
 * @param connection  The connection for talking to this actor.
 * @param myFactory  The factory that created this actor.
 * @param actionTime  How long the user has to act before being kicked off,
 *    in milliseconds.
 */
internal class GatekeeperActor(
        connection: Connection,
        private val myFactory: GatekeeperActorFactory,
        actionTime: Int,
        private val gorgel: Gorgel,
        timer: Timer,
        commGorgel: Gorgel,
        mustSendDebugReplies: Boolean) : RoutingActor(connection, myFactory.refTable(), commGorgel, mustSendDebugReplies), BasicProtocolActor {

    /** True if actor has been disconnected.  */
    private var amLoggedOut = false

    /** Optional convenience label for logging and such.  */
    private var label: String? = null

    /**
     * Test if this actor is an authenticated administrator.
     *
     * @return true if this actor is an authenticated administrator.
     *
     * @see .doAuth
     */
    private var isAdmin = false

    /** Timeout for kicking off users who connect and don't either request a
     * reservation or authenticate as an administrator.  */
    private var myActionTimeout: Timeout? = timer.after(actionTime.toLong(), object : TimeoutNoticer {
        override fun noticeTimeout() {
            disconnectAfterTimeout()
        }
    })

    /**
     * Cancel the reservation timeout, because the user is real.
     */
    private fun becomeLive() {
        myActionTimeout?.cancel()
        myActionTimeout = null
    }

    /**
     * Handle loss of connection from the user.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        doDisconnect()
        gorgel.i?.run { info("${this@GatekeeperActor} connection died: $connection") }
    }

    /**
     * Do the actual work of authorizing an administrator.
     *
     * After a call to this method returns true, this actor will be an
     * authorized administrator, meaning that [isAdmin] will return true
     * and [ensureAuthorizedAdmin] will succeed without throwing an
     * exception.
     *
     * This method is invoked in response to receipt of an "auth" message by
     * the message handler in [BasicProtocolActor].
     *
     * @param handler  The handler that is requesting the authorization (not
     * used here).
     * @param auth  Authorization information from the authorization request.
     * @param newLabel  The label string from the authorization request.
     *
     * @return true if the given arguments are sufficient to authorize
     * administrative access to this server, false if not.
     */
    override fun doAuth(handler: BasicProtocolHandler, auth: AuthDesc?, newLabel: String): Boolean {
        becomeLive()
        label = newLabel
        if (myFactory.verifyAuthorization(auth) && myFactory.allowAdmin) {
            isAdmin = true
        }
        return isAdmin
    }

    /**
     * Disconnect this actor from the server.
     */
    override fun doDisconnect() {
        if (!amLoggedOut) {
            gorgel.i?.run { info("disconnecting ${this@GatekeeperActor}") }
            amLoggedOut = true
            close()
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do admin operations.
     *
     * @throws MessageHandlerException if this actor is not authorized to
     * perform administrative operations.
     *
     * @see .doAuth
     */
    fun ensureAuthorizedAdmin() {
        if (!isAdmin) {
            doDisconnect()
            throw MessageHandlerException("actor $this attempted admin operation without authorization")
        }
    }

    /**
     * Get a printable representation of this actor.
     *
     * @return a printable representation of this actor.
     */
    override fun toString() = label ?: super.toString()

    private fun disconnectAfterTimeout() {
        if (myActionTimeout != null) {
            myActionTimeout = null
            doDisconnect()
        }
    }
}
