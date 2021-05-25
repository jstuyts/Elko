package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.objectdatabase.ObjectDatabase
import org.elkoserver.server.gatekeeper.Authorizer
import org.elkoserver.server.gatekeeper.Gatekeeper
import org.elkoserver.server.gatekeeper.ReservationResult
import org.elkoserver.server.gatekeeper.ReservationResultHandler
import org.elkoserver.server.gatekeeper.SetPasswordResultHandler
import java.util.Random
import java.util.function.Consumer
import kotlin.math.abs

/**
 * A simple implementation of the [Authorizer] interface for use with
 * the Elko Gatekeeper.
 *
 * Invoked using reflection, so the signature cannot be changed.
 *
 * @param myRandom Random number generator, for generating IDs.
 */
class PasswdAuthorizer(
    private val myRandom: Random,
    private val myGatekeeper: Gatekeeper,
    private val myObjectDatabase: ObjectDatabase,
    private val amAnonymousOK: Boolean,
    private val myActorIDBase: String) : Authorizer {

    /**
     * Add an actor to the actor table.
     *
     * @param actor  The actor description for the actor to add.
     */
    fun addActor(actor: ActorDesc) {
        myObjectDatabase.putObject("a-${actor.id}", actor, null)
    }

    /**
     * Add a place to the place table.
     *
     * @param name  The name of the place.
     * @param context  The context 'name' maps to.
     */
    fun addPlace(name: String, context: String) {
        val place = PlaceDesc(name, context)
        myObjectDatabase.putObject("p-$name", place, null)
    }

    /**
     * Write a changed actor to the database.
     *
     * @param actor  The actor description for the actor to checkpoint.
     */
    fun checkpointActor(actor: ActorDesc) {
        myObjectDatabase.putObject("a-${actor.id}", actor, null)
    }

    /**
     * Generate a new, random actor ID.
     *
     * @return a new actor ID.
     */
    fun generateID(): String {
        val idNumber = abs(myRandom.nextLong())
        return "$myActorIDBase-$idNumber"
    }

    /**
     * Obtain the description of an actor.
     *
     * @param actorID  The ID of the actor of interest.
     * @param handler  Handler to be called with the actor description object
     * when retrieved.
     */
    fun getActor(actorID: String, handler: Consumer<Any?>) {
        myObjectDatabase.getObject("a-$actorID", handler)
    }

    /**
     * Obtain the context that a place name maps to.
     *
     * @param name  The name of the place of interest.
     * @param handler  Handler to be called with place description object when
     * retrieved.
     */
    fun getPlace(name: String, handler: Consumer<Any?>) {
        myObjectDatabase.getObject("p-$name", handler)
    }

    /**
     * Remove an actor from the actor table.
     *
     * @param actorID  The ID of the actor to be removed.
     * @param handler  Handler to be called with deletion result.
     */
    fun removeActor(actorID: String, handler: Consumer<Any?>?) {
        myObjectDatabase.removeObject("a-$actorID", handler)
    }

    /**
     * Remove an entry from the place table.
     *
     * @param name  The name of the place to remove.
     */
    fun removePlace(name: String) {
        myObjectDatabase.removeObject("p-$name", null)
    }

    /**
     * Service a request to make a reservation.  This method is called each
     * time the Gatekeeper receives a 'reserve' request from a client.
     *
     * @param protocol  The protocol the reservation seeker wants to use.
     * @param context  The context they wish to enter.
     * @param id  The user who is asking for the reservation.
     * @param name  Optional legible name for the user.
     * @param password  Password tendered for entry, if relevant.
     * @param handler  Object to receive results of reservation check, once
     * available.
     */
    override fun reserve(protocol: String, context: String, id: String?,
                         name: String?, password: String?,
                         handler: ReservationResultHandler
    ) {
        if (id == null && !amAnonymousOK) {
            handler.handleFailure("anonymous reservations not allowed")
        } else {
            val runnable: Consumer<Any?> = ReserveRunnable(handler, protocol, context, id, name,
                    password)
            id?.let { getActor(it, runnable) }
            getPlace(context, runnable)
        }
    }

    private inner class ReserveRunnable(private val myHandler: ReservationResultHandler, private val myProtocol: String,
                                        private val myContextName: String, private var myID: String?, private var myName: String?, private val myPassword: String?) : Consumer<Any?> {
        private var myComponentCount = 0
        private var myActor: ActorDesc? = null
        private var myContextID: String? = null
        override fun accept(obj: Any?) {
            /* This method normally gets entered twice, once for the context
               and once for the actor.  These can be distinguished by looking
               at the type of 'obj'.  However, failure to fetch an object is
               indicated by receiving a null and null is untyped, making it
               harder to sort out the failure cases.  A failure to fetch the
               context is not really a failure at all; it just means that the
               context is unnamed.  A failure to fetch the actor is a failure,
               regardless of the availability of context information.

               Basically, this means there are four possible cases:
                 -- actor + context: the success case for a named context.
                 -- actor + null: the success case for an unnamed context.
                 -- context + null: the failure case for a named context.
                 -- null + null: the failure case for an unnamed context.

               However, if 'id' is null, then the user is connecting
               anonymously and there will be no actor object arriving.  In that
               case this method will be entered just once and will always
               succeed with either a named or an unnamed context.
            */
            var failure: String? = null
            var reservation: ReservationResult? = null
            ++myComponentCount
            if (obj != null) {
                when (obj) {
                    is ActorDesc -> myActor = obj
                    is PlaceDesc -> myContextID = obj.contextID
                    is ReservationResult -> {
                        reservation = obj
                        failure = reservation.deny
                    }
                    else -> throw Error("bad object class: ${obj.javaClass}")
                }
            }
            if (myComponentCount == (if (myID == null) 1 else 2)) {
                if (myContextID == null) {
                    myContextID = myContextName
                }
                var iid: String? = null
                val currentActor = myActor
                if (currentActor != null) {
                    if (currentActor.testPassword(myPassword)) {
                        if (myName == null) {
                            myName = currentActor.name
                        }
                        myID = currentActor.id
                        iid = currentActor.internalID()
                    } else {
                        failure = "bad password"
                    }
                } else if (myID != null) {
                    failure = "no such actor"
                }
                if (failure == null) {
                    myGatekeeper.requestReservation(myProtocol, myContextID!!, iid!!, this)
                }
            }
            if (failure != null) {
                myHandler.handleFailure(failure)
            } else if (reservation != null) {
                myHandler.handleReservation(reservation.actor,
                        reservation.contextID,
                        myName,
                        reservation.hostport,
                        reservation.auth)
            }
        }

    }

    /**
     * Service a request to change a user's password.  This method is called
     * each time the Gatekeeper receives a 'setpassword' request from a client.
     *
     * @param id  The user who is asking for this.
     * @param oldPassword  Current password, to check for permission.
     * @param newPassword  The new password.
     * @param handler  Object to receive results, when done.
     */
    override fun setPassword(id: String, oldPassword: String?,
                             newPassword: String?,
                             handler: SetPasswordResultHandler
    ) {
        getActor(id) { obj: Any? ->
            val failure =
                    if (obj != null) {
                        val actor = obj as ActorDesc
                        if (actor.testPassword(oldPassword)) {
                            if (actor.canSetPass) {
                                actor.setPassword(newPassword)
                                checkpointActor(actor)
                                null
                            } else {
                                "password change not allowed"
                            }
                        } else {
                            "bad password"
                        }
                    } else {
                        "no such actor"
                    }
            handler.handle(failure)
        }
    }

    /**
     * Shut down the authorization service.
     */
    override fun shutdown() {
        myObjectDatabase.shutdown()
    }
}
