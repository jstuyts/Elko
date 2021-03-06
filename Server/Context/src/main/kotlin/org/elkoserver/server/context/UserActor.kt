package org.elkoserver.server.context

import com.grack.nanojson.JsonObject
import org.elkoserver.foundation.actor.BasicProtocolActor
import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.actor.RoutingActor
import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.json.DispatchTarget
import org.elkoserver.foundation.json.SourceRetargeter
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.server.context.model.BasicObject
import org.elkoserver.server.context.model.Context
import org.elkoserver.server.context.model.User
import org.elkoserver.server.context.model.UserActorProtocol
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag
import java.util.LinkedList
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Actor representing a connection to a user in one or more contexts.
 *
 * @param myConnection  Connection associated with this user.
 * @param myContextor  The contextor for this server.
 * @param amAuthRequired  True if this use needs to tender a reservation in
 *    order to enter.
 * @param protocol  Protocol being used on the new connection
 * @param myIdGenerator Counter for assigning ephemeral user IDs.
 */
class UserActor(
    private val myConnection: Connection,
    private val myContextor: Contextor,
    private val runner: Executor,
    private val amAuthRequired: Boolean,
    override val protocol: String,
    private val userActorGorgel: Gorgel,
    private val userGorgelWithoutRef: Gorgel,
    private val timer: Timer,
    commGorgel: Gorgel,
    private val myIdGenerator: IdGenerator,
    mustSendDebugReplies: Boolean
) : RoutingActor(myConnection, myContextor.realRefTable, commGorgel, mustSendDebugReplies), SourceRetargeter,
    BasicProtocolActor, UserActorProtocol {
    /** The users this actor is the actor for, by context.  */
    private val myUsers: MutableMap<Context, User> = HashMap()

    /** Flag to prevent race in exit where users bump off themselves.  */
    private var amDead = false

    /** Timeout for kicking off users who connect and don't enter a context.  */
    private var myEntryTimeout: Timeout? = null

    /**
     * Evict and disconnect the user before they're even in.
     *
     * @param why  Explanation string to send them as the last thing they see
     * from before the connection drops.
     * @param whyCode  Machine readable code tag version of 'why'
     */
    private fun abruptExit(why: String, whyCode: String) {
        send(msgExit(myContextor.session, why, whyCode, false))
        userActorGorgel.i?.run { info("abrupt exit: $why") }
        if (!amDead && myUsers.isEmpty()) {
            userActorGorgel.i?.run { info("abrupt exit disconnects pre-entry user") }
            amDead = true
            close()
        }
    }

    /**
     * Handle loss of connection from the user.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(
        connection: Connection,
        reason: Throwable
    ) {
        if (!amDead) {
            amDead = true
            val users: List<User> = LinkedList(myUsers.values)
            runner.execute({
                for (user in users) {
                    user.connectionDied(connection)
                }
            })
            close()
        }
    }

    /**
     * Authorize (or refuse authorization for) a connection for this actor.
     * In the case of a UserActor, we don't participate in the authorization
     * abstraction at all, so we always just say no.
     *
     * @param handler  Handler requesting the authorization.
     * @param auth  Authorization information from the authorization request
     * message, or null if no authorization information was provided.
     * @param newLabel  Label (e.g., displayable user name) string from the
     * authorization request message, or "&lt;anonymous&gt;" if no value was
     * specified.
     *
     * @return true if the authorization succeeded and the session should be
     * allowed to proceed, false if it did not and the session should be
     * disconnected.
     */
    override fun doAuth(handler: BasicProtocolHandler, auth: AuthDesc?, newLabel: String): Boolean = false

    /**
     * Obtain this user actors's connection ID.
     *
     * @return the ID number of the connection associated with this actor.
     */
    override fun connectionID(): Int = myConnection.id()

    /**
     * Disconnect this actor.
     */
    override fun doDisconnect() {
        connectionDied(myConnection, Exception("user disconnect"))
    }

    /**
     * Enter the user into a context.
     *
     * The user connection will be dropped if: (1) the user reference they ask
     * for is already in use, (2) it is an invalid user reference, (3) the
     * context they are asking for doesn't exist, (4) the server is full, or
     * (5) the connection they are coming in over requires reservations to be
     * used and they haven't provided a valid reservation authorization code.
     *
     * @param userRef  The user ID they claim, or null if none was asserted.
     * @param name  The name they want to have, or null if none was asserted.
     * @param contextRef  The context they want to enter.
     * @param contextTemplate  Optional reference the template context from
     * which the context should be derived.
     * @param sess  Client session ID for the connection to the context, or
     * null if the client doesn't care.
     * @param auth  Optional authorization code for entry, if needed.
     * @param utag  Constructor tag string for synthetic user, or null if none
     * @param uparam  Arbitrary object parameterizing synthetic user, or null
     * @param debug  This session will use debug settings, if enabled.
     * @param scope  Application scope for filtering mods
     */
    fun enterContext(
        userRef: String?, name: String?, contextRef: String,
        contextTemplate: String?, sess: String?, auth: String?,
        utag: String?, uparam: JsonObject?, debug: Boolean,
        scope: String?
    ) {
        var actualUserRef = userRef
        var actualName = name
        userActorGorgel.i?.run { info("attempting to enter context $contextRef") }
        val currentEntryTimeout = myEntryTimeout
        if (currentEntryTimeout != null) {
            currentEntryTimeout.cancel()
            myEntryTimeout = null
        }
        if (debug) {
            myConnection.setDebugMode(true)
        }
        if (0 < myContextor.limit &&
            myContextor.limit <= myContextor.userCount()
        ) {
            abruptExit("server full", "full")
            return
        }
        var isEphemeral = false
        var isAnonymous = false
        if (utag != null) {
            actualUserRef = null
            isAnonymous = false
            if (actualName == null) {
                actualName = "_synth_${myIdGenerator.generate()}"
            }
        } else if (actualUserRef == null) {
            actualUserRef = "user-anon"
            isEphemeral = true
            isAnonymous = true
            if (actualName == null) {
                actualName = "_anon_${myIdGenerator.generate()}"
            }
        }
        var opener: DirectorActor? = null
        if (amAuthRequired) {
            if (auth == null) {
                abruptExit("reservation required", "nores")
                return
            }
            val reservation = if (isEphemeral) {
                myContextor.lookupReservation(null, contextRef, auth)
            } else {
                myContextor.lookupReservation(actualUserRef, contextRef, auth)
            }
            if (reservation == null) {
                abruptExit("invalid reservation", "badres")
                return
            }
            opener = reservation.issuer
            reservation.redeem()
        }
        val runnable = EnterRunnable(actualUserRef, isEphemeral, isAnonymous, actualName, contextRef, sess)
        if (utag != null) {
            myContextor.synthesizeUser(myConnection, utag, uparam, contextRef, contextTemplate, runnable)
        } else {
            myContextor.loadUser(actualUserRef!!, scope, runnable)
        }
        myContextor.getOrLoadContext(contextRef, contextTemplate, runnable, opener)
    }

    /**
     * Runnable to handle asynchronous database retrieval of relevant objects
     * needed to process an enter request.  This will be invoked twice: once
     * for the user object and once for the context object.  The order in which
     * the two objects are delivered is not important.  On the second
     * invocation it checks to see if both objects were successfully delivered
     * by the database: If so, it processes the entry normally.  If not, the
     * user is kicked off.
     */
    private inner class EnterRunnable(
        private var myUserRef: String?, private val amEphemeral: Boolean, private val amAnonymous: Boolean,
        private val myEntryName: String?, private val myContextRef: String, private val mySess: String?
    ) : Consumer<Any?> {
        private var myUser: User? = null
        private var myContext: Context? = null
        private var myComponentCount = 0
        override fun accept(obj: Any?) {
            if (amDead) {
                /* User disconnected before getting all the way in. */
                return
            }
            ++myComponentCount
            if (obj is User) {
                myUser = obj
            } else if (obj is Context) {
                myContext = obj
            }
            if (myComponentCount == 2) {
                val currentUser = myUser
                if (currentUser == null) {
                    abruptExit("invalid user $myUserRef", "baduser")
                } else {
                    val currentContext = myContext
                    if (currentContext == null) {
                        abruptExit("invalid context $myContextRef", "badcontext")
                    } else {
                        if (myUserRef == null) {
                            myUserRef = currentUser.ref()
                        }
                        if (myUserRef == null) {
                            myUserRef = myContextor.uniqueID("u")
                        }
                        var name = currentUser.name
                        if (name == null) {
                            name = myEntryName!!
                        }
                        val subID = myContextor.uniqueID("")
                        val ref = myUserRef + subID
                        myUsers[currentContext] = currentUser
                        currentUser.activate(
                            ref, subID, myContextor, name, mySess,
                            amEphemeral, amAnonymous, this@UserActor,
                            userGorgelWithoutRef.withAdditionalStaticTags(Tag("ref", ref))
                        )
                        currentUser.checkpoint()
                        val problem = currentUser.enterContext(currentContext)
                        myContextor.noteUser(currentUser, true)
                        if (problem != null) {
                            currentUser.exitContext(problem, problem, false)
                        }
                    }
                }
            }
        }

    }

    /**
     * Remove this actor from one of the contexts that it is in.
     */
    override fun exitContext(context: Context?) {
        if (context != null) {
            myUsers.remove(context)
        }
        if (!amDead && myUsers.isEmpty()) {
            startEntryTimeout()
        }
    }

    /**
     * Return the the object that should be the treated as the source of a
     * message.  In the case of a UserActor, this is the User object.
     *
     * @param target  The object to which the message is addressed.
     *
     * @return an object that should be presented to the message handler as the
     * source of a message to 'target' in place of this object.
     */
    override fun findEffectiveSource(target: DispatchTarget): Deliverer? =
        if (target is BasicObject) {
            val context = target.context()
            myUsers[context]
        } else {
            this
        }

    /**
     * Initiate a timeout waiting for the user to enter a context.  If the
     * timeout trips before the user acts, the user will be disconnected.
     */
    private fun startEntryTimeout() {
        myEntryTimeout = timer.after(myContextor.entryTimeout.toLong()) {
            if (myEntryTimeout != null) {
                myEntryTimeout = null
                abruptExit("entry timeout", "timeout")
            }
        }
    }

    /**
     * Get the user associated with this actor in some context.
     *
     * @param context  The context with which the caller is concerned.
     *
     * @return the User associated with this actor in the given context.
     */
    fun user(context: Context): User? = myUsers[context]

    init {
        startEntryTimeout()
    }
}
