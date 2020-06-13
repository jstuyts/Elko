package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.actor.NonRoutingActor
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.util.trace.TraceFactory
import java.util.function.Consumer

/**
 * Actor representing a gatekeeper's connection to its director.
 *
 * Since this is a non-routing actor, it implements its message protocol
 * directly.  This protocol consists of one message:
 *
 * 'reserve' - The return result from a reservation request that was
 * previously sent to the director.
 *
 * @param connection  The connection for actually communicating.
 * @param dispatcher  Message dispatcher for incoming messages.
 * @param myFactory  The factory that created this actor.
 * @param director  Host description for the director connected to.
 */
internal class DirectorActor(
        connection: Connection,
        dispatcher: MessageDispatcher,
                             private val myFactory: DirectorActorFactory,
        director: HostDesc,
        traceFactory: TraceFactory,
        mustSendDebugReplies: Boolean) : NonRoutingActor(connection, dispatcher, traceFactory, mustSendDebugReplies) {

    /** Flag that this connection is still active.  */
    private var amLive = true

    /** Reservation requests that have been issued to the director, the
     * responses to which have not yet been received to.  */
    private val myPendingReservations: MutableMap<String, Consumer<in ReservationResult>> = HashMap()

    /**
     * Handle loss of connection from the director.
     *
     * @param connection  The director connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        traceFactory.comm.eventm("lost director connection $connection: $reason")
        amLive = false
        myFactory.setDirector(null)
        for ((key, handler) in myPendingReservations) {
            val sep = key.indexOf('|')
            handler.accept(ReservationResult(key.take(sep), key.substring(sep + 1), "director connection lost"))
        }
    }

    /**
     * Issue a request for a reservation to the director.
     *
     * @param protocol  The protocol for the requested reservation.
     * @param context  The requested context.
     * @param actor  The requested actor.
     * @param handler  Object to handle result.
     */
    fun requestReservation(protocol: String, context: String, actor: String, handler: Consumer<in ReservationResult>) {
        if (!amLive) {
            handler.accept(ReservationResult(context, actor, "director connection lost"))
        } else {
            myPendingReservations["$context|$actor"] = handler
            send(msgReserve(this, protocol, context, actor))
        }
    }

    /**
     * Get this object's reference string.  This singleton object's reference
     * string is always 'director'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "director"

    /**
     * Handle the 'reserve' verb.
     *
     * Process the reservation result that was returned (or the failure).
     *
     * @param context  The context reserved.
     * @param optActor  The actor reserved for.
     * @param optHostport  The host:port to connect to.
     * @param optAuth  The authorization code to connect with.
     * @param optDeny  Error message in the case of failure.
     */
    @JSONMethod("context", "user", "hostport", "reservation", "deny")
    fun reserve(from: DirectorActor, context: String, optActor: OptString, optHostport: OptString, optAuth: OptString, optDeny: OptString) {
        val hostport = optHostport.value<String?>(null)
        val auth = optAuth.value<String?>(null)
        val deny = optDeny.value<String?>(null)
        val actor = optActor.value<String?>(null)
        val nonNullActor = actor ?: ""
        var contextKey = context
        var haveAllSlashesBeenProcessed = false
        var handler: Consumer<in ReservationResult>?
        do {
            handler = myPendingReservations.remove("$contextKey|$nonNullActor")
            if (handler == null) {
                val slash = contextKey.lastIndexOf('-')
                if (slash > -1) {
                    contextKey = contextKey.take(slash)
                } else {
                    haveAllSlashesBeenProcessed = true
                }
            }
        } while (handler == null && !haveAllSlashesBeenProcessed)
        when {
            handler == null -> traceFactory.comm.errorm("received unexpected reservation for $context $nonNullActor")
            deny == null -> handler.accept(ReservationResult(context, actor!!, hostport, auth))
            else -> handler.accept(ReservationResult(context, actor!!, deny))
        }
    }

    companion object {
        /**
         * Create a 'reserve' message.
         *
         * @param target  Object the message is being sent to.
         * @param protocol  Desired protocol.
         * @param context  Context to enter.
         * @param actor Who wants to enter.
         */
        private fun msgReserve(target: Referenceable, protocol: String,
                               context: String, actor: String?) =
                JSONLiteralFactory.targetVerb(target, "reserve").apply {
                    addParameter("protocol", protocol)
                    addParameter("context", context)
                    addParameterOpt("user", actor)
                    finish()
                }
    }

    init {
        myFactory.setDirector(this)
        send(msgAuth(this, director.auth, myFactory.gatekeeper.serverName()))
    }
}
