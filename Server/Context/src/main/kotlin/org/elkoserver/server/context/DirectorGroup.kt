package org.elkoserver.server.context

import org.elkoserver.foundation.actor.Actor
import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServerLoadMonitor
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.json.JsonLiteral
import org.elkoserver.server.context.model.Context
import org.elkoserver.server.context.model.User
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Outbound group containing all the connected directors.
 *
 * @param server  Server object.
 * @param contextor  The server contextor.
 * @param directors  List of HostDesc objects describing directors with
 *    whom to register.
 * @param listeners  List of HostDesc objects describing active
 *    listeners to register with the indicated directors.
 */
class DirectorGroup(
    server: Server,
    loadMonitor: ServerLoadMonitor,
    contextor: Contextor,
    directors: List<HostDesc>,
    internal val listeners: List<HostDesc>,
    gorgel: Gorgel,
    messageDispatcher: MessageDispatcher,
    private val reservationFactory: ReservationFactory,
    private val directorActorFactory: DirectorActorFactory,
    timer: Timer,
    props: ElkoProperties,
    connectionRetrierFactory: ConnectionRetrierFactory
) : OutboundGroup(
    "conf.register",
    server,
    contextor,
    directors,
    gorgel,
    messageDispatcher,
    timer,
    props,
    connectionRetrierFactory
) {

    /** Iterator for cycling through arbitrary relays.  */
    private var myDirectorPicker: Iterator<Deliverer>? = null

    /** Pending reservations.  */
    private val myReservations: MutableMap<Reservation, Reservation> = HashMap()

    /* ----- required OutboundGroup methods ----- */
    /**
     * Obtain the class of actors in this group, in this case, DirectorActor.
     *
     * @return this group's actor class.
     */
    override fun actorClass(): Class<DirectorActor> = DirectorActor::class.java

    /**
     * Obtain a printable string suitable for tagging this group in log
     * messages and so forth, in this case, "director".
     *
     * @return this group's label string.
     */
    override fun label(): String = "director"

    /**
     * Get an actor object suitable to act on message traffic on a new
     * connection in this group.
     *
     * @param connection  The new connection
     * @param dispatcher   Message dispatcher for the message protocol on the
     * new connection
     * @param host  Descriptor information for the host the new connection is
     * connected to
     *
     * @return a new Actor object for use on this new connection
     */
    override fun provideActor(connection: Connection, dispatcher: MessageDispatcher, auth: AuthDesc): Actor {
        val director = directorActorFactory.create(connection, dispatcher, this, auth)
        updateDirector(director)
        return director
    }

    /**
     * Obtain a broker service string describing the type of service that
     * connections in this group want to connect to, in this case,
     * "director-provider".
     *
     * @return a broker service string for this group.
     */
    override fun service() = "director-provider"

    /* ----- DirectorGroup methods ----- */
    /**
     * Add a new, pending reservation to the reservation table.
     *
     * @param reservation  The reservation to add.
     */
    fun addReservation(reservation: Reservation) {
        myReservations[reservation] = reservation
    }

    /**
     * Get the user capacity of this context server.
     *
     * @return the capacity of this server.
     */
    fun capacity(): Int = contextor.limit

    /**
     * Lookup a reservation.
     *
     * @param who  Whose reservation?
     * @param where  For where?
     * @param authCode  The alleged authCode.
     *
     * @return the requested reservation if there is one, or null if not.
     */
    fun lookupReservation(who: String?, where: String, authCode: String): Reservation? {
        val key = reservationFactory.create(who, where, authCode)
        return myReservations[key]
    }

    /**
     * Tell the directors that a context has been opened or closed.
     *
     * @param context  The context.
     * @param open  true if opened, false if closed.
     */
    fun noteContext(context: Context, open: Boolean) {
        val opener = context.opener
        val ref = context.ref()
        val maxCap = context.maxCapacity
        val baseCap = context.baseCapacity
        val restricted = context.isRestricted
        if (opener != null) {
            opener.send(
                msgContext(
                    ref, open, true, maxCap, baseCap,
                    restricted
                )
            )
            sendToNeighbors(
                opener,
                msgContext(
                    ref, open, false, maxCap, baseCap,
                    restricted
                )
            )
        } else {
            send(msgContext(ref, open, false, maxCap, baseCap, restricted))
        }
    }

    /**
     * Tell the directors that a context gate has been opened or closed.
     *
     * @param context  The context whose gate is being opened or closed
     * @param open  Flag indicating open or closed
     * @param reason  Reason for closing the gate
     */
    fun noteContextGate(context: Context, open: Boolean, reason: String?) {
        send(msgGate(context.ref(), open, reason))
    }

    /**
     * Tell the directors that a user has come or gone.
     *
     * @param user  The user.
     * @param on  true if now online, false if now offline.
     */
    fun noteUser(user: User, on: Boolean) {
        send(msgUser(user.context().ref(), user.ref(), on))
    }

    /**
     * Pick an arbitrary director.  This is done by a simple round-robin.
     */
    private fun pickADirector(): DirectorActor? {
        while (true) {
            val currentDirectorPicker = myDirectorPicker
            if (currentDirectorPicker == null || !currentDirectorPicker.hasNext()) {
                myDirectorPicker = members().iterator()
                if (!currentDirectorPicker!!.hasNext()) {
                    return null
                }
            }
            myDirectorPicker = try {
                return currentDirectorPicker.next() as DirectorActor
            } catch (e: ConcurrentModificationException) {
                null
            }
        }
    }

    /**
     * Push a user to a different context: obtain a reservation for the new
     * context from one of our directors, send it to the user, and then kick
     * them out.
     *
     * @param who  The user being pushed
     * @param contextRef  The ref of the context to push them to.
     */
    fun pushNewContext(who: User, contextRef: String) {
        val director = pickADirector()
        if (director != null) {
            director.pushNewContext(who, contextRef)
        } else {
            who.exitContext("no director for context push", "nodir", false)
        }
    }

    /**
     * Relay a message to other context servers via a director.
     *
     * @param target  The target of the message.
     * @param contextRef  Contexts to deliver to, or null if don't care.
     * @param userRef  Users to deliver to, or null if don't care.
     * @param message  The message to relay.
     */
    fun relay(target: String?, contextRef: String?, userRef: String?, message: JsonLiteral) {
        /* If relayer is null, assume there are no directors and thus no
           relaying to be done. */
        pickADirector()?.send(msgRelay("provider", contextRef, userRef, message))
    }

    /**
     * Remove an expired or redeemed reservation from the reservation table.
     *
     * @param reservation  The reservation to remove.
     */
    fun removeReservation(reservation: Reservation) {
        myReservations.remove(reservation)
    }

    /**
     * Update a newly connected director as to what contexts and users are
     * open.
     *
     * @param director  The director to be updated.
     */
    private fun updateDirector(director: DirectorActor) {
        for (context in contextor.contexts()) {
            director.send(
                msgContext(
                    context.ref(), true, false,
                    context.maxCapacity,
                    context.baseCapacity,
                    context.isRestricted
                )
            )
            if (context.gateIsClosed()) {
                director.send(msgGate(context.ref(), true, context.gateClosedReason!!))
            }
        }
        for (user in contextor.users()) {
            director.send(msgUser(user.context().ref(), user.ref(), true))
        }
    }

    companion object {
        /** Default reservation expiration time, in seconds.  */
        internal const val DEFAULT_RESERVATION_EXPIRATION_TIMEOUT = 30
    }

    init {
        loadMonitor.registerLoadWatcher { loadFactor -> send(msgLoad(loadFactor)) }
        connectHosts()
    }
}
