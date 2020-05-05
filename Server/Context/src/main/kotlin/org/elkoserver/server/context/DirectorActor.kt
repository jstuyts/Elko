package org.elkoserver.server.context

import org.elkoserver.foundation.actor.NonRoutingActor
import org.elkoserver.foundation.json.DispatchTarget
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.JsonObject
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.Msg.msgSay
import org.elkoserver.util.trace.TraceFactory
import java.util.LinkedList
import java.util.NoSuchElementException

/**
 * Actor representing a connection to a director.
 *
 * @param connection  The connection for actually communicating.
 * @param dispatcher  Message dispatcher for incoming messages.
 * @param myGroup  The send group for all the directors.
 * @param host  Host description for this connection.
 */
class DirectorActor(connection: Connection, dispatcher: MessageDispatcher,
                    private val myGroup: DirectorGroup, host: HostDesc, private val timer: Timer, traceFactory: TraceFactory) : NonRoutingActor(connection, dispatcher, traceFactory) {

    /** Map from tag strings to users awaiting reservations.  */
    private val myPendingReservationRequests: MutableMap<String, User> = HashMap()

    /** Counter for generating tag strings for new reservation requests.  */
    private var myNextReservationTag = 1

    /**
     * Handle loss of connection from the director.
     *
     * @param connection  The director connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        traceFactory.comm.eventm("lost director connection $connection: $reason")
        myGroup.expelMember(this)
    }

    /**
     * Special iterator to iterate through all of a context's clones or all of
     * a user's clones or the intersection of a set of a user clones and a set
     * of context clones.
     *
     * Note that this class has the form of Iterator but it does not
     * actually implement the Iterator interface because some of its methods
     * need to declare exceptions.
     */
    private inner class RelayIterator internal constructor(context: OptString, user: OptString) {
        private var myMode = 0
        private val myContexts: Iterator<DispatchTarget>
        private var myUsers: Iterator<DispatchTarget>
        private val myContextRef = context.value<String?>(null)
        private val myUserRef = user.value<String?>(null)
        private var myActiveContext: Context? = null
        private var myNextResult: Any? = null

        operator fun hasNext() =
                if (myNextResult == null) {
                    try {
                        myNextResult = next()
                        true
                    } catch (e: NoSuchElementException) {
                        false
                    }
                } else {
                    true
                }

        private fun lookupClones(ref: String?) =
                if (ref == null) {
                    emptyList()
                } else {
                    val result = myGroup.contextor().clones(ref)
                    if (result.isEmpty()) {
                        throw MessageHandlerException("$ref not found")
                    } else {
                        result
                    }
                }

        private fun nextUser() =
                try {
                    myUsers.next() as User
                } catch (e: ClassCastException) {
                    throw MessageHandlerException("user reference $myUserRef does not refer to a user")
                }

        private fun nextContext() =
                try {
                    myContexts.next() as Context
                } catch (e: ClassCastException) {
                    throw MessageHandlerException("context reference $myContextRef does not refer to a context")
                }

        operator fun next(): Any {
            val currentNextResult = myNextResult
            if (currentNextResult != null) {
                val result: Any = currentNextResult
                myNextResult = null
                return result
            }
            return when (myMode) {
                MODE_CONTEXT -> nextContext()
                MODE_USER -> nextUser()
                MODE_USER_IN_CONTEXT -> {
                    while (true) {
                        while (myUsers.hasNext()) {
                            val user = nextUser()
                            if (user.context() === myActiveContext) {
                                return user
                            }
                        }
                        myActiveContext = nextContext()
                        myUsers = lookupClones(myUserRef).iterator()
                    }
                }
                else -> throw Error("internal error: invalid mode $myMode")
            }
        }

        init {
            myContexts = lookupClones(myContextRef).iterator()
            myUsers = lookupClones(myUserRef).iterator()
            if (myContexts.hasNext()) {
                if (myUsers.hasNext()) {
                    myMode = MODE_USER_IN_CONTEXT
                    myActiveContext = nextContext()
                } else {
                    myMode = MODE_CONTEXT
                }
            } else {
                myMode = if (myUsers.hasNext()) {
                    MODE_USER
                } else {
                    throw MessageHandlerException(
                            "missing context and/or user parameters")
                }
            }
        }
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'provider'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "provider"

    /**
     * Remove an expired or redeemed reservation from the reservation table.
     *
     * @param reservation  The reservation to remove.
     */
    fun removeReservation(reservation: Reservation) {
        myGroup.removeReservation(reservation)
    }

    /**
     * Handle the 'close' verb.
     *
     * Process a directive to close a context or evict a user.
     *
     * @param context  The context to close, or omitted if not relevant.
     * @param user  The user to evict, or omitted if not relevant.
     * @param dup  True if this is being done to eliminated a duplicate
     * context.
     */
    @JSONMethod("context", "user", "dup")
    fun close(from: DirectorActor, context: OptString, user: OptString, dup: OptBoolean) {
        val isDup = dup.value(false)

        /* Must copy lists of dead users and contexts to avert
           ConcurrentModificationException from user.exitContext() and
           context.forceClose(). */
        val deadUsers: MutableList<User> = LinkedList()
        val deadContexts: MutableList<Context> = LinkedList()
        val relayIter = RelayIterator(context, user)
        while (relayIter.hasNext()) {
            val obj = relayIter.next()
            if (obj is Context) {
                deadContexts.add(obj)
            } else  /* if (obj instanceof User) */ {
                deadUsers.add(obj as User)
            }
        }
        for (deadUser in deadUsers) {
            deadUser.exitContext("admin", "admin", isDup)
        }
        for (deadContext in deadContexts) {
            deadContext.forceClose(isDup)
        }
    }

    /**
     * Handle the 'doreserve' verb.
     *
     * Process an entry reservation for a user about to enter a context.
     *
     * @param context  The context to be entered.
     * @param user  The user who will enter.
     * @param reservation  Authorization code for entry.
     */
    @JSONMethod("context", "user", "reservation")
    fun doreserve(from: DirectorActor, context: String, user: OptString, reservation: String) {
        myGroup.addReservation(Reservation(user.value<String?>(null), context,
                reservation, myGroup.reservationTimeout(), from, timer, traceFactory))
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Process a directive to reinitialize relationships with directors.
     */
    @JSONMethod
    fun reinit(from: DirectorActor) {
        myGroup.contextor().reinitServer()
    }

    /**
     * Handle the 'relay' verb.
     *
     * Relay a message to various entities on this server.
     */
    @JSONMethod("context", "user", "msg")
    fun relay(from: DirectorActor, context: OptString, user: OptString, msg: JsonObject) {
        val iter = RelayIterator(context, user)
        while (iter.hasNext()) {
            myGroup.contextor().deliverMessage(iter.next() as BasicObject, msg)
        }
    }

    fun pushNewContext(who: User, contextRef: String) {
        val tag = myNextReservationTag++.toString()
        myPendingReservationRequests[tag] = who
        timer.after(INTERNAL_RESERVATION_TIMEOUT.toLong(), object : TimeoutNoticer {
            override fun noticeTimeout() {
                val user = myPendingReservationRequests.remove(tag)
                user?.exitContext("no response", "badres", false)
            }
        })
        send(msgReserve(this, who.protocol(), contextRef, who.baseRef(), tag))
    }

    /**
     * Handle the 'reserve' verb.
     *
     * Process a reply to a reservation request issued from here.
     *
     * @param from  The user asking for the reservation.
     * @param optUser  The user who is asking for this.
     * @param optTag  Optional tag for requestor to match
     */
    @JSONMethod("context", "user", "hostport", "reservation", "deny", "tag")
    fun reserve(from: DirectorActor, context: String, optUser: OptString,
                optHostPort: OptString, optReservation: OptString,
                optDeny: OptString, optTag: OptString) {
        val tag = optTag.value<String?>(null)
        val hostPort = optHostPort.value<String?>(null)
        val reservation = optReservation.value<String?>(null)
        val deny = optDeny.value<String?>(null)
        if (tag != null) {
            val who = myPendingReservationRequests.remove(tag)
            if (who == null) {
                traceFactory.comm.warningi("received reservation for unknown tag $tag")
            } else if (deny != null) {
                who.exitContext(deny, "dirdeny", false)
            } else if (hostPort == null) {
                who.exitContext("no hostport for next context", "dirfail",
                        false)
            } else if (reservation == null) {
                who.exitContext("no reservation for next context", "dirfail",
                        false)
            } else {
                who.exitWithContextChange(context, hostPort, reservation)
            }
        } else {
            traceFactory.comm.warningi("received reservation reply without tag")
        }
    }

    /**
     * Handle the 'say' verb.
     *
     * Process a directive to send text to a context or a user.
     *
     * @param contextRef  Context to broadcast to, or omitted if not relevant.
     * @param userRef  The user to send to, or omitted if not relevant.
     * @param text  The message to transmit.
     */
    @JSONMethod("context", "user", "text")
    fun say(from: DirectorActor, contextRef: OptString, userRef: OptString, text: String) {
        val iter = RelayIterator(contextRef, userRef)
        while (iter.hasNext()) {
            val obj = iter.next()
            if (obj is Context) {
                val context = obj
                context.send(msgSay(context, null, text))
            } else  /* if (obj instanceof User) */ {
                val user = obj as User
                user.send(msgSay(user, null, text))
            }
        }
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Process a directive to shut down the server.
     *
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    @JSONMethod("kill")
    fun shutdown(from: DirectorActor, kill: OptBoolean) {
        myGroup.contextor().shutdownServer(kill.value(false))
    }

    companion object {
        /** How long to wait for a director to give us a reservation.  */
        private const val INTERNAL_RESERVATION_TIMEOUT = 10 * 1000

        private const val MODE_CONTEXT = 1
        private const val MODE_USER = 2
        private const val MODE_USER_IN_CONTEXT = 3

        /**
         * Create an 'address' message.
         *
         * @param target  Object the message is being sent to.
         * @param protocol  The protocol to reach this server.
         * @param hostPort  This server's address, as far as the rest of world is
         * concerned.
         */
        private fun msgAddress(target: Referenceable, protocol: String, hostPort: String) =
                JSONLiteralFactory.targetVerb(target, "address").apply {
                    addParameter("protocol", protocol)
                    addParameter("hostport", hostPort)
                    finish()
                }

        /**
         * Create a 'reserve' message.
         *
         * @param target  Object the message is being sent to.
         * @param protocol  The desired protocol for the reservation
         * @param contextRef  The context the reservation is sought for
         * @param userRef  The user for whom the reservation is sought
         * @param tag  Tag for matching responses with requests
         */
        private fun msgReserve(target: Referenceable, protocol: String, contextRef: String, userRef: String, tag: String) =
                JSONLiteralFactory.targetVerb(target, "reserve").apply {
                    addParameter("protocol", protocol)
                    addParameter("context", contextRef)
                    addParameterOpt("user", userRef)
                    addParameterOpt("tag", tag)
                    finish()
                }

        /**
         * Create a 'willserve' message.
         *
         * @param target  Object the message is being sent to.
         * @param context  The context family that will be served.
         * @param capacity  Maximum number of users that will be served.
         * @param restricted  True if the context family is restricted
         */
        private fun msgWillserve(target: Referenceable, context: String, capacity: Int, restricted: Boolean) =
                JSONLiteralFactory.targetVerb(target, "willserve").apply {
                    addParameter("context", context)
                    if (capacity > 0) {
                        addParameter("capacity", capacity)
                    }
                    if (restricted) {
                        addParameter("restricted", true)
                    }
                    finish()
                }
    }

    init {
        myGroup.admitMember(this)
        send(msgAuth(this, host.auth(), myGroup.contextor().serverName()))
        for (listener in myGroup.listeners()) {
            if ("reservation" == listener.auth()!!.mode()) {
                send(msgAddress(this, listener.protocol()!!, listener.hostPort()!!))
            }
        }
        for (family in myGroup.contextor().contextFamilies()) {
            if (family.startsWith("$")) {
                send(msgWillserve(this, family.substring(1), myGroup.capacity(),
                        true))
            } else {
                send(msgWillserve(this, family, myGroup.capacity(), false))
            }
        }
    }
}