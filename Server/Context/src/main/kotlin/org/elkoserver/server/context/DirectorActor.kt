package org.elkoserver.server.context

import org.elkoserver.foundation.actor.NonRoutingActor
import org.elkoserver.foundation.actor.msgAuth
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.json.JsonObject
import org.elkoserver.util.trace.slf4j.Gorgel
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
class DirectorActor(
        connection: Connection,
        dispatcher: MessageDispatcher,
        private val myGroup: DirectorGroup,
        host: HostDesc,
        private val reservationFactory: ReservationFactory,
        private val timer: Timer,
        gorgel: Gorgel,
        mustSendDebugReplies: Boolean) : NonRoutingActor(connection, dispatcher, gorgel, mustSendDebugReplies) {

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
        gorgel.i?.run { info("lost director connection $connection: $reason") }
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
        private val myContextRef = context.value<String?>(null)
        private val myContexts = lookupClones(myContextRef).iterator()
        private val myUserRef = user.value<String?>(null)
        private var myUsers = lookupClones(myUserRef).iterator()
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
                    val result = myGroup.contextor.refTable.clones(ref)
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
            if (myContexts.hasNext()) {
                if (myUsers.hasNext()) {
                    myMode = MODE_USER_IN_CONTEXT
                    myActiveContext = nextContext()
                } else {
                    myMode = MODE_CONTEXT
                }
            } else {
                myMode = if (myUsers.hasNext()) MODE_USER else throw MessageHandlerException( "missing context and/or user parameters")
            }
        }
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'provider'.
     *
     * @return a string referencing this object.
     */
    override fun ref(): String = "provider"

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
    @JsonMethod("context", "user", "dup")
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
    @JsonMethod("context", "user", "reservation")
    fun doreserve(from: DirectorActor, context: String, user: OptString, reservation: String) {
        myGroup.addReservation(reservationFactory.create(user.value<String?>(null), context,
                reservation, from))
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Process a directive to reinitialize relationships with directors.
     */
    @JsonMethod
    fun reinit(from: DirectorActor) {
        myGroup.contextor.reinitServer()
    }

    /**
     * Handle the 'relay' verb.
     *
     * Relay a message to various entities on this server.
     */
    @JsonMethod("context", "user", "msg")
    fun relay(from: DirectorActor, context: OptString, user: OptString, msg: JsonObject) {
        val iter = RelayIterator(context, user)
        while (iter.hasNext()) {
            myGroup.contextor.deliverMessage(iter.next() as BasicObject, msg)
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
    @JsonMethod("context", "user", "hostport", "reservation", "deny", "tag")
    fun reserve(from: DirectorActor, context: String, optUser: OptString,
                optHostPort: OptString, optReservation: OptString,
                optDeny: OptString, optTag: OptString) {
        val tag = optTag.value<String?>(null)
        val hostPort = optHostPort.value<String?>(null)
        val reservation = optReservation.value<String?>(null)
        val deny = optDeny.value<String?>(null)
        if (tag != null) {
            val who = myPendingReservationRequests.remove(tag)
            when {
                who == null -> gorgel.warn("received reservation for unknown tag $tag")
                deny != null -> who.exitContext(deny, "dirdeny", false)
                hostPort == null -> who.exitContext("no hostport for next context", "dirfail", false)
                reservation == null -> who.exitContext("no reservation for next context", "dirfail", false)
                else -> who.exitWithContextChange(context, hostPort, reservation)
            }
        } else {
            gorgel.warn("received reservation reply without tag")
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
    @JsonMethod("context", "user", "text")
    fun say(from: DirectorActor, contextRef: OptString, userRef: OptString, text: String) {
        val iter = RelayIterator(contextRef, userRef)
        while (iter.hasNext()) {
            val obj = iter.next()
            if (obj is Context) {
                obj.send(msgSay(obj, null, text))
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
     */
    @JsonMethod
    fun shutdown(from: DirectorActor) {
        myGroup.contextor.shutdownServer()
    }

    companion object {
        /** How long to wait for a director to give us a reservation.  */
        private const val INTERNAL_RESERVATION_TIMEOUT = 10 * 1000

        private const val MODE_CONTEXT = 1
        private const val MODE_USER = 2
        private const val MODE_USER_IN_CONTEXT = 3
    }

    init {
        myGroup.admitMember(this)
        send(msgAuth(this, host.auth, myGroup.contextor.serverName()))
        myGroup.listeners
                .filter { "reservation" == it.auth.mode }
                .forEach { send(msgAddress(this, it.protocol!!, it.hostPort!!)) }
        for (family in myGroup.contextor.contextFamilies) {
            if (family.startsWith("$")) {
                send(msgWillserve(this, family.substring(1), myGroup.capacity(),
                        true))
            } else {
                send(msgWillserve(this, family, myGroup.capacity(), false))
            }
        }
    }
}