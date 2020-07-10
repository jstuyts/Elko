package org.elkoserver.server.context

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.Contents.Companion.sendContentsDescription
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.LinkedList
import java.util.NoSuchElementException

/**
 * A [Context] is a place for interaction between connected users.  It
 * is one of the three basic object types (along with [User] and [ ]).
 *
 * @param name  Context name.
 * @param maxCapacity   Maximum number of users allowed (-1 if unlimited).
 * @param baseCapacity   Maximum number of users before cloning (-1, the
 * default, if context is not to be cloned).
 * @param isSemiPrivate  Flag that context is semi-private (false by
 * default).
 * @param isEntryRestricted  Flag that context is subject to entry
 * restriction (false by default).
 * @param isContentAgnostic  Flag that context has no beliefs about its
 * contents (false by default).
 * @param isMultiEntry  Flag that is true if a user may enter this context
 * multiple times concurrently (false by default).
 * @param mods Mods for the new context (null if it has no mods).
 * @param userMods  Templates for mods to attach to entering users.
 * @param ref  Optional reference string for this context object.
 * @param subscribe  Optional array of presence domains to subscribe to
 * @param isEphemeral  Flag that context is ephemeral, i.e., changes won't
 * be persisted) (false by default)
 * @param isAllowableTemplate  Flag that context may be used as a template
 * for other contexts (false by default).
 * @param isMandatoryTemplate  Flag that context may be only used as a
 * template for other contexts, but not instantiated directly (false by
 * default).
 * @param isAllowAnonymous  Flag that context permits anonymous users to
 * enter (false by default).
 */
class Context @JsonMethod("name", "capacity", "basecapacity", "semiprivate", "restricted", "agnostic", "multientry", "mods", "?usermods", "?contents", "ref", "?subscribe", "ephemeral", "template", "templateonly", "allowanonymous")
internal constructor(name: String,
                     internal val maxCapacity: Int, baseCapacity: OptInteger,
                     isSemiPrivate: OptBoolean, isEntryRestricted: OptBoolean,
                     isContentAgnostic: OptBoolean, isMultiEntry: OptBoolean,
                     mods: Array<Mod>, userMods: Array<Mod>?, contents: Array<Item>?, ref: OptString,
                     subscribe: Array<String>?, isEphemeral: OptBoolean,
                     isAllowableTemplate: OptBoolean, isMandatoryTemplate: OptBoolean,
                     isAllowAnonymous: OptBoolean) : BasicObject(name, mods, true, contents), Deliverer {
    /** Send group for users in this context.  */
    internal lateinit var group: LiveGroup
        private set

    /** Maximum number of users allowed in the context before it clones.  */
    internal val baseCapacity = baseCapacity.value(-1)

    /** True if users in this context can't see one another.  */
    val isSemiPrivate: Boolean = isSemiPrivate.value(false)

    /** True if entry to this context is access controlled.  */
    val isRestricted: Boolean = isEntryRestricted.value(false)

    /** True if anonymous users are allowed in this context.  */
    private val amAllowAnonymous: Boolean = isAllowAnonymous.value(false)

    /** True if this context's contents aren't managed by the context itself  */
    private val amContentAgnostic: Boolean = isContentAgnostic.value(false)

    /** True if this context may be used as a template for other contexts  */
    private val amAllowableTemplate: Boolean = isAllowableTemplate.value(false)

    /**
     * Test if this context may be used as a template for other contexts but
     * may not be instantiated directly.
     *
     * @return true iff this context is a mandatory template context.
     */
    /** True if this context may only be used as a template  */
    val isMandatoryTemplate: Boolean = isMandatoryTemplate.value(false)

    /** Mods to attach to users when they arrive.  */
    private val myUserMods: Array<Mod>? = userMods

    /** Presence domains that this context subscribes to.  Empty if not
     * subscribing to any, null if not providing presence information.  */
    internal val subscriptions: Array<String>? = subscribe

    /* Fields below here only apply to active contexts. */
    /** Number of users currently in the context.  */
    var userCount: Int = 0
        private set

    /** Users here by base ref, or null if this context is multientry.  */
    private var myUsers: MutableMap<String?, User?>? = null

    /** Number of retainers holding the context open.  */
    private var myRetainCount = 0

    /** Ref of context descriptor from which this context was loaded.  */
    private lateinit var loadedFromRef: String

    /** Director who originally requested this context to be opened, if any.  */
    internal var opener: DirectorActor? = null
        private set

    /** Entities that want to be notified when users arrive or depart.  */
    private var myUserWatchers: MutableList<UserWatcher>? = null

    /** Entities that want to be notified when the context is shut down.  */
    private var myContextShutdownWatchers: MutableList<ContextShutdownWatcher>? = null

    /** True if context is being shut down, thus blocking new entries.  */
    private var amClosing = false

    /** True if context shut down is being forced, ignoring retain count  */
    private var amForceClosing = false

    /** Reason this context is closed to user entry, or null if it is not.  */
    internal var gateClosedReason: String? = null
        private set

    /** Optional watcher for friend presence changes.  */
    private var myPresenceWatcher: PresenceWatcher? = null

    private lateinit var timer: Timer

    /**
     * Activate a context.
     * @param ref  Reference string for the new context.
     * @param subID  Clone sub identity, or the empty string for non-clones.
     * @param isEphemeral  True if this context is ephemeral (won't checkpoint)
     * @param contextor  Contextor for this server.
     * @param loadedFromRef  Reference string for the context descriptor that
     * this context was loaded from
     * @param opener Director who requested this context to be opened, or null
     * if not relevant.
     */
    fun activate(ref: String, subID: String, isEphemeral: Boolean,
                 contextor: Contextor, loadedFromRef: String,
                 opener: DirectorActor?, gorgel: Gorgel, timer: Timer) {
        super.activate(ref, subID, isEphemeral, contextor, gorgel)
        this.timer = timer
        group = LiveGroup()
        userCount = 0
        myRetainCount = 0
        myUserWatchers = null
        myContextShutdownWatchers = null
        this.opener = opener
        this.loadedFromRef = loadedFromRef
        amClosing = false
        amForceClosing = false
        contextor.noteContext(this, true)
    }

    /**
     * Add a new mod to this context.  The mod must be a [ContextMod]
     * even though the method is declared generically.  If it is not, it will
     * not be added, and an error message will be written to the log.
     *
     * @param mod  The mod to attach; must be a [ContextMod].
     */
    override fun attachMod(mod: Mod) {
        if (mod is ContextMod) {
            super.attachMod(mod)
            if (mod is PresenceWatcher) {
                myPresenceWatcher = mod
            }
        } else {
            myGorgel.error("attempt to attach non-ContextMod $mod")
        }
    }

    /**
     * Attach the context-specified user mods for this context to a user.
     *
     * @param who  The user to attach the mods to.
     */
    fun attachUserMods(who: User) {
        if (myUserMods != null) {
            for (mod in myUserMods) {
                try {
                    val newMod = mod.clone() as Mod
                    newMod.markAsEphemeral()
                    newMod.attachTo(who)
                } catch (e: CloneNotSupportedException) {
                    myGorgel.error("Mod class ${mod.javaClass} does not support clone")
                }
            }
        }
    }

    /**
     * If nobody is using this context any more, checkpoint and discard it.
     */
    private fun checkForContextShutdown() {
        if (userCount == 0 && (myRetainCount == 0 || amForceClosing)) {
            if (!amClosing) {
                amClosing = true
                myGorgel.info("shutting down")
                noteContextShutdown()
                checkpoint()
                assertActivated {
                    it.remove(this)
                    it.noteContext(this, false)
                }
            }
        }
    }

    /**
     * Close this context's gate, blocking new users from entering.
     *
     * @param reason  String describing why this is being done.
     */
    fun closeGate(reason: String?) {
        gateClosedReason = reason ?: "context closed to new entries"
        assertActivated { it.noteContextGate(this, false, reason) }
    }

    /**
     * Place a user into the context.
     *
     * @param who  The user to place.
     *
     * @return null if successful, or an error message string if not.
     */
    fun enterContext(who: User): String? {
        /* This looks like a bug, but isn't. It is correct to increment the
           count here, even if entry ends up being prevented, since a blocked
           entry will result in user exit and thus a call to exitContext()
           that will decrement the count again. */
        userCount += 1
        return if (isRestricted && !who.entryEnabled(myRef!!)) {
            myGorgel.info("$who forbidden entry (entry restricted)")
            "restricted"
        } else if (!amAllowAnonymous && who.isAnonymous) {
            myGorgel.info("$who forbidden entry (anonymous users forbidden)")
            "noanonymity"
        } else if (gateClosedReason != null) {
            myGorgel.info("$who forbidden entry (gate closed: $gateClosedReason)")
            "gateclosed"
        } else if (userCount > maxCapacity && maxCapacity != -1) {
            myGorgel.info("$who forbidden entry (capacity limit reached)")
            "full"
        } else if (amClosing) {
            myGorgel.info("$who forbidden entry (context is closing)")
            "contextclose"
        } else {
            val currentUsers = myUsers
            if (currentUsers != null) {
                val prev = currentUsers[who.baseRef()]
                if (prev != null && !who.isEphemeral) {
                    myGorgel.info("expelling $prev due to reentry as $who")
                    prev.send(msgExit(this, "duplicate entry", "dupentry", false))
                    prev.forceDisconnect()
                }
                currentUsers[who.baseRef()] = who
            }
            sendContextDescription(who, assertActivated(Contextor::session))
            noteUserArrival(who)
            myGorgel.info("$who enters")
            null
        }
    }

    /**
     * Remove a user from the context.
     *
     * @param who  The user to remove.
     */
    fun exitContext(who: User) {
        myGorgel.info("$who exits")
        val currentUsers = myUsers
        if (currentUsers?.get(who.baseRef()) != null && currentUsers[who.baseRef()]!!.ref() == who.ref()) {
            currentUsers.remove(who.baseRef())
        }
        if (who.isArrived) {
            if (!isSemiPrivate) {
                send(msgDelete(who))
            }
            noteUserDeparture(who)
        }
        --userCount
        checkForContextShutdown()
    }

    /**
     * Close this context, even if it has been retained by one or more calls to
     * the [.retain] method, and even if there are still users in it
     * (this means kicking those users off).
     */
    fun forceClose() {
        forceClose(false)
    }

    /**
     * Close this context, even if it has been retained by one or more calls to
     * the [.retain] method, and even if there are still users in it
     * (this means kicking those users off).
     *
     * @param dup  true if this is being done to eliminate a duplicate context.
     */
    fun forceClose(dup: Boolean) {
        amForceClosing = true
        val members: List<Deliverer> = LinkedList(group.members())
        members
                .map { it as User }
                .forEach { it.exitContext("context closing", "contextclose", dup) }
    }

    /**
     * Test if this context's gate is closed.  If the gate is closed, new users
     * may not enter, even if the context is not full.
     *
     * @return true iff this context's gate is closed.
     */
    fun gateIsClosed(): Boolean = gateClosedReason != null

    /**
     * Look up an object in this context's namespace.  Note that the context's
     * namespace is not the context's contents but the namespace of object
     * identifiers that the context uses for resolving object references.  This
     * may be (indeed, normally is) shared with other active contexts on the
     * same server.
     *
     * @param ref  Reference string denoting the object desired.
     *
     * @return the object corresponding to 'ref', or null if there is no such
     * object in the context's namespace.
     */
    operator fun get(ref: String): BasicObject? {
        return if (ref == "context") {
            this
        } else {
            assertActivated { it.refTable[ref] as BasicObject }
        }
    }

    /**
     * Look up one of this server's static objects.
     *
     * @param ref  Reference string denoting the object of interest.
     *
     * @return the static object corresponding to 'ref', or null if there is no
     * such object in the server's static object table.
     */
    fun getStaticObject(ref: String): Any? = assertActivated { it.getStaticObject(ref) }

    /**
     * Test if this context may be used as a template for other contexts.
     *
     * @return true iff this context is an allowable template context.
     */
    val isAllowableTemplate: Boolean
        get() = amAllowableTemplate || isMandatoryTemplate

    /**
     * Notify anybody who has expressed an interest in this context shutting
     * down.
     */
    private fun noteContextShutdown() {
        myContextShutdownWatchers?.forEach(ContextShutdownWatcher::noteContextShutdown)
    }

    /**
     * Notify anybody who has expressed an interest that somebody has arrived
     * in this context.
     *
     * @param who  The user who arrived
     */
    private fun noteUserArrival(who: User) {
        myUserWatchers?.forEach { watcher ->
            watcher.noteUserArrival(who)
        }
    }

    /**
     * Notify anybody who has expressed an interest that somebody has departed
     * from this context.
     *
     * @param who  The user who departed
     */
    private fun noteUserDeparture(who: User) {
        myUserWatchers?.forEach { watcher ->
            watcher.noteUserDeparture(who)
        }
    }

    /**
     * Take notice that a user elsewhere has come or gone.
     *
     * @param observerRef  Ref of user in this context who allegedly cares
     * @param domain  Presence domain of relationship between users
     * @param whoRef  Ref of user who came or went
     * @param whereRef  Ref of context the entered or exited
     * @param on  True if they came, false if they left
     */
    fun observePresenceChange(observerRef: String, domain: String?, whoRef: String, whereRef: String, on: Boolean) {
        myPresenceWatcher?.notePresenceChange(observerRef, domain, whoRef, whereRef, on)
        val observer = myUsers!![observerRef]
        if (observer != null) {
            observer.observePresenceChange(observerRef, domain, whoRef, whereRef, on)
        } else {
            myGorgel.warn("presence change of $whoRef${if (on) " entering " else " exiting "}$whereRef directed to unknown user $observerRef")
        }
    }

    /**
     * Open this context's gate, allowing new users in if the context is not
     * full.
     */
    fun openGate() {
        gateClosedReason = null
        assertActivated { it.noteContextGate(this, true, null) }
    }

    /**
     * Register a callback to be invoked when the context is shut down.  Any
     * number of such callbacks may be registered.  The callback will be
     * invoked after all users have gone but immediately before the context is
     * checkpointed.  In particular, shutdown watchers may make changes to the
     * persistable state that will be checkpointed when the context is finally
     * shut down.
     *
     * @param watcher  An object to notify when the context is shut down.
     */
    fun registerContextShutdownWatcher(watcher: ContextShutdownWatcher) {
        val currentContextShutdownWatchers = myContextShutdownWatchers
        val actualContextShutdownWatchers = if (currentContextShutdownWatchers == null) {
            val newContextShutdownWatchers = LinkedList<ContextShutdownWatcher>()
            myContextShutdownWatchers = newContextShutdownWatchers
            newContextShutdownWatchers
        } else {
            currentContextShutdownWatchers
        }
        actualContextShutdownWatchers.add(watcher)
    }

    /**
     * Register a callback to be invoked when a user enters or exits the
     * context.  Any number of such callbacks may be registered.
     *
     * @param watcher  An object to notify when a user arrives.
     */
    fun registerUserWatcher(watcher: UserWatcher) {
        val currentUserWatchers = myUserWatchers
        val actualUserWatchers = if (currentUserWatchers == null) {
            val newUserWatchers = LinkedList<UserWatcher>()
            myUserWatchers = newUserWatchers
            newUserWatchers
        } else {
            currentUserWatchers
        }
        actualUserWatchers.add(watcher)
    }

    /**
     * Release an earlier call to [.retain].  When `release` has
     * been called the same number of times as [.retain] has been, the
     * context is free to shut down when empty.  If the context is already
     * empty, it will be shut down immediately.  Calls to this method in excess
     * of the number of calls to [.retain] will be ignored.
     */
    private fun release() {
        if (myRetainCount > 0) {
            --myRetainCount
            checkForContextShutdown()
        }
    }

    /**
     * Keep this context open even if all users exit (normally a context will
     * be shut down automatically after the last user leaves).  Each call to
     * `retain` must be matched by a corresponding call to [ ][.release] in order for the context to be permitted to close normally
     * (though it can still be closed by called [.forceClose]).
     */
    private fun retain() {
        myRetainCount += 1
    }

    /**
     * Schedule a timer event associated with this context.  This is different
     * from scheduling a timer event directly using the Timer class in two
     * significant ways: first, it ensures that the context is retained until
     * after the event happens; second, it executes the event handler thunk on
     * the server's run queue instead of in the Timer thread, so that we won't
     * get reentrancy.
     *
     * Another notable difference is that unlike direct Timer events, there is
     * no explicit cancellation mechanism.  However, since the Timer's
     * cancellation mechanism is not really as useful as it might at first
     * appear, this is not as signficant.
     *
     * @param millis  How long to wait until timing out.
     * @param thunk  Thunk to be run when the timeout happens.
     */
    fun scheduleContextEvent(millis: Long, thunk: Runnable) {
        retain()
        timer.after(millis, ContextEventThunk(thunk))
    }

    /**
     * Class to hold onto a context event thunk so that when the timer triggers
     * the event, the thunk is executed on the server run queue and the context
     * is then released.
     */
    private inner class ContextEventThunk internal constructor(private val myThunk: Runnable) : Runnable, TimeoutNoticer {
        override fun noticeTimeout() {
            assertActivated { it.server.enqueue(this) }
        }

        override fun run() {
            try {
                myThunk.run()
            } finally {
                release()
            }
        }

    }

    /**
     * Transmit a description of this context as a series of 'make' messages,
     * such that the receiver will be able to construct a local presence of it.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address the message(s) to.
     */
    override fun sendObjectDescription(to: Deliverer, maker: Referenceable) {
        sendContextDescription(to, maker)
    }

    /**
     * Transmit a description of this context as a series of "make" messages.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address message to.
     */
    private fun sendContextDescription(to: Deliverer, maker: Referenceable) {
        var sess: String? = null
        if (to is User) {
            sess = to.sess
        }
        to.send(msgMake(maker, this, sess))
        if (!isSemiPrivate) {
            group.members()
                    .filterIsInstance<User>()
                    .forEach { it.sendUserDescription(to, this, false) }
        }
        if (!amContentAgnostic) {
            sendContentsDescription(to, this, myContents)
        }
        to.send(msgReady(this))
    }

    /**
     * Send a message to everyone in this context save one.  This message will
     * be delivered to every client whose user is currently in the context
     * except the one specified by the 'exclude' parameter.
     *
     * @param exclude  Who to exclude from the send operation.
     * @param message  The message to send.
     */
    fun sendToNeighbors(exclude: Deliverer, message: JsonLiteral) {
        group.sendToNeighbors(exclude, message)
    }

    /**
     * Obtain a Deliverer that will deliver to all of a user's neighbors in
     * this context.
     *
     * @param exclude  Who to exclude from the send operation.
     *
     * @return a Deliverer that wraps Context.sendToNeighbors
     */
    fun neighbors(exclude: Deliverer): Deliverer {
        return object : Deliverer {
            override fun send(message: JsonLiteral) {
                sendToNeighbors(exclude, message)
            }
        }
    }

    /**
     * Obtain a printable string representation of this context.
     *
     * @return a printable representation of this context.
     */
    override fun toString(): String = "Context '${ref()}'"

    private inner class UserIterator internal constructor() : MutableIterator<User> {
        private val myInnerIterator = group.members().iterator()
        private var myNext: User?
        override fun hasNext(): Boolean = myNext != null

        override fun next(): User {
            return if (myNext != null) {
                val result: User = myNext!!
                myNext = nextUser
                result
            } else {
                throw NoSuchElementException()
            }
        }

        override fun remove() {
            throw UnsupportedOperationException()
        }

        private val nextUser: User?
            get() {
                while (myInnerIterator.hasNext()) {
                    val next = myInnerIterator.next()
                    if (next is User) {
                        return next
                    }
                }
                return null
            }

        init {
            myNext = nextUser
        }
    }

    /**
     * Obtain an iterator over the users currently in this context.  Note that
     * this iterator is only valid within a single turn.
     *
     * @return an @{link java.util.Iterator} over this context's current users.
     */
    fun userIterator(): Iterator<User> = UserIterator()

    /**
     * Handle the 'exit' verb.
     *
     * Exit the context and disconnect the user who sent it.
     */
    @JsonMethod
    fun exit(from: Deliverer) {
        val fromUser = from as User
        fromUser.exitContext("normal exit", "bye", false)
    }
    /* ----- BasicObject overrides ----------------------------------------- */
    /**
     * Obtain the context this object is associated with.
     *
     * @return the context itself.
     */
    override fun context(): Context = this

    /**
     * Test if this object is a container.  (Note: in this case, it is.)
     *
     * @return true -- all contexts are containers.
     */
    override val isContainer: Boolean
        get() = true

    /**
     * Return the proper type tag for this object.
     *
     * @return a type tag string for this kind of object; in this case,
     * "context".
     */
    override fun type(): String = "context"

    /**
     * Obtain the user this object is currently contained by.
     *
     * @return null, since a context is never contained by a user.
     */
    override fun user(): User? = null
    /* ----- Deliverer interface ------------------------------------------- */
    /**
     * Send a message to everyone in this context.  The message will be
     * delivered to every client whose user is currently in the context.
     *
     * @param message  The message to send.
     */
    override fun send(message: JsonLiteral) {
        group.send(message)
    }
    /* ----- Encodable. interface, inherited from BasicObject -------------- */
    /**
     * Encode this context for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this context.
     */
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("context", control).apply {
                if (control.toClient()) {
                    addParameter("ref", myRef)
                } else {
                    addParameter("capacity", maxCapacity)
                    if (baseCapacity != -1) {
                        addParameter("basecapacity", baseCapacity)
                    }
                    if (isSemiPrivate) {
                        addParameter("semiprivate", isSemiPrivate)
                    }
                    if (isRestricted) {
                        addParameter("restricted", isRestricted)
                    }
                    if (amContentAgnostic) {
                        addParameter("agnostic", amContentAgnostic)
                    }
                    if (amAllowableTemplate) {
                        addParameter("template", true)
                    }
                    if (isMandatoryTemplate) {
                        addParameter("templateonly", true)
                    }
                    if (myUsers == null) {
                        addParameter("multientry", true)
                    }
                    if (subscriptions != null) {
                        addParameter("subscribe", subscriptions)
                    }
                }
                addParameter("name", name)
                val mods = myModSet.encode(control)
                if (mods.size > 0) {
                    addParameter("mods", mods)
                }
                if (control.toRepository() && myUserMods != null) {
                    val userMods = JsonLiteralArray(control)
                    for (mod in myUserMods) {
                        userMods.addElement(mod)
                    }
                    if (userMods.size > 0) {
                        addParameter("usermods", userMods)
                    }
                }
                finish()
            }

    companion object {
        /**
         * Obtain a Deliverer that will deliver to an arbitrary list of users.
         *
         * @param toList  List of users to deliver to
         *
         * @return a Deliverer that wraps toList
         */
        fun toList(toList: List<BasicObject>): Deliverer {
            return object : Deliverer {
                override fun send(message: JsonLiteral) {
                    toList
                            .map { it as User }
                            .forEach { it.send(message) }
                }
            }
        }

        /**
         * Obtain a Deliverer that will deliver to an arbitrary list of users
         * except for one distinguished user.
         *
         * @param toList  List of users to deliver to
         * @param exclude  The one to exclude
         *
         * @return a Deliverer that wraps toList, taking note to exclude the one
         * odd user out
         */
        fun toListExcluding(toList: List<BasicObject>,
                            exclude: Deliverer): Deliverer {
            return object : Deliverer {
                override fun send(message: JsonLiteral) {
                    toList
                            .map { it as User }
                            .filter { it != exclude }
                            .forEach { it.send(message) }
                }
            }
        }
    }

    init {
        if (isEphemeral.value(false)) {
            markAsEphemeral()
        }
        if (!isMultiEntry.value(false)) {
            myUsers = HashMap()
        }
    }
}
