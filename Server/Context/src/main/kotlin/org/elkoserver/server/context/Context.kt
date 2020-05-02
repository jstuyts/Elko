package org.elkoserver.server.context

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.Contents.Companion.sendContentsDescription
import org.elkoserver.server.context.Msg.msgDelete
import org.elkoserver.server.context.Msg.msgExit
import org.elkoserver.server.context.Msg.msgMake
import org.elkoserver.server.context.Msg.msgReady
import org.elkoserver.util.trace.Trace
import java.util.HashMap
import java.util.LinkedList
import java.util.NoSuchElementException

/**
 * A [Context] is a place for interaction between connected users.  It
 * is one of the three basic object types (along with [User] and [ ]).
 *
 * @param name  Context name.
 * @param myMaxCapacity   Maximum number of users allowed (-1 if unlimited).
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
class Context @JSONMethod("name", "capacity", "basecapacity", "semiprivate", "restricted", "agnostic", "multientry", "mods", "?usermods", "?contents", "ref", "?subscribe", "ephemeral", "template", "templateonly", "allowanonymous")
internal constructor(name: String,
                     private val myMaxCapacity: Int, baseCapacity: OptInteger,
                     isSemiPrivate: OptBoolean, isEntryRestricted: OptBoolean,
                     isContentAgnostic: OptBoolean, isMultiEntry: OptBoolean,
                     mods: Array<Mod>, userMods: Array<Mod>?, contents: Array<Item>?, ref: OptString,
                     subscribe: Array<String>?, isEphemeral: OptBoolean,
                     isAllowableTemplate: OptBoolean, isMandatoryTemplate: OptBoolean,
                     isAllowAnonymous: OptBoolean) : BasicObject(name, mods, true, contents), Deliverer {
    /** Send group for users in this context.  */
    private var myGroup: LiveGroup? = null

    /** Maximum number of users allowed in the context before it clones.  */
    private val myBaseCapacity = baseCapacity.value(-1)

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
    private val mySubscriptions: Array<String>? = subscribe

    /* Fields below here only apply to active contexts. */
    /** Number of users currently in the context.  */
    private var myUserCount = 0

    /** Users here by base ref, or null if this context is multientry.  */
    private var myUsers: MutableMap<String?, User?>? = null

    /** Number of retainers holding the context open.  */
    private var myRetainCount = 0

    /** Ref of context descriptor from which this context was loaded.  */
    private var myLoadedFromRef: String? = null

    /** Director who originally requested this context to be opened, if any.  */
    private var myOpener: DirectorActor? = null

    /** Entities that want to be notified when users arrive or depart.  */
    private var myUserWatchers: MutableList<UserWatcher>? = null

    /** Entities that want to be notified when the context is shut down.  */
    private var myContextShutdownWatchers: MutableList<ContextShutdownWatcher>? = null

    /** True if context is being shut down, thus blocking new entries.  */
    private var amClosing = false

    /** True if context shut down is being forced, ignoring retain count  */
    private var amForceClosing = false

    /** Reason this context is closed to user entry, or null if it is not.  */
    private var myGateClosedReason: String? = null

    /** Optional watcher for friend presence changes.  */
    private var myPresenceWatcher: PresenceWatcher? = null

    /** Trace object for diagnostics.  */
    private var tr: Trace? = null
    private var timer: Timer? = null

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
     * @param appTrace  Trace object for diagnostics.
     */
    fun activate(ref: String, subID: String, isEphemeral: Boolean,
                 contextor: Contextor, loadedFromRef: String?,
                 opener: DirectorActor?, appTrace: Trace?, timer: Timer?) {
        super.activate(ref, subID, isEphemeral, contextor)
        this.timer = timer
        tr = appTrace
        myGroup = LiveGroup()
        myUserCount = 0
        myRetainCount = 0
        myUserWatchers = null
        myContextShutdownWatchers = null
        myOpener = opener
        myLoadedFromRef = loadedFromRef
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
            tr!!.errorm("attempt to attach non-ContextMod " + mod + " to " +
                    this)
        }
    }

    /**
     * Attach the context-specified user mods for this context to a user.
     *
     * @param who  The user to attach the mods to.
     */
    fun attachUserMods(who: User?) {
        if (myUserMods != null) {
            for (mod in myUserMods) {
                try {
                    val newMod = mod.clone() as Mod
                    newMod.markAsEphemeral()
                    newMod.attachTo(who!!)
                } catch (e: CloneNotSupportedException) {
                    tr!!.errorm("Mod class " + mod.javaClass +
                            " does not support clone")
                }
            }
        }
    }

    /**
     * Obtain the number of users who may enter (a clone of) this context
     * before another clone must be created.  When the user count of the
     * context reaches this number, users may still enter this specific clone
     * if they specify its cloned context ID explicitly (and as long as [ ][.maxCapacity] is not exceeded), but users who request entry to the
     * context by specifying its generic context ID (that is, its ID before
     * cloning) will be directed to a different clone.
     *
     * @return the number of users who may enter before the context clones.
     */
    fun baseCapacity(): Int {
        return myBaseCapacity
    }

    /**
     * If nobody is using this context any more, checkpoint and discard it.
     */
    private fun checkForContextShutdown() {
        if (myUserCount == 0 && (myRetainCount == 0 || amForceClosing)) {
            if (!amClosing) {
                amClosing = true
                tr!!.eventi("shutting down $this")
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
        myGateClosedReason = reason ?: "context closed to new entries"
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
        myUserCount += 1
        return if (isRestricted && !who.entryEnabled(myRef!!)) {
            tr!!.eventi(who.toString() + " forbidden entry to " + this +
                    " (entry restricted)")
            "restricted"
        } else if (!amAllowAnonymous && who.isAnonymous) {
            tr!!.eventi(who.toString() + " forbidden entry to " + this +
                    " (anonymous users forbidden)")
            "noanonymity"
        } else if (myGateClosedReason != null) {
            tr!!.eventi(who.toString() + " forbidden entry to " + this +
                    " (gate closed: " + myGateClosedReason + ")")
            "gateclosed"
        } else if (myUserCount > myMaxCapacity && myMaxCapacity != -1) {
            tr!!.eventi(who.toString() + " forbidden entry to " + this +
                    " (capacity limit reached)")
            "full"
        } else if (amClosing) {
            tr!!.eventi(who.toString() + " forbidden entry to " + this +
                    " (context is closing)")
            "contextclose"
        } else {
            if (myUsers != null) {
                val prev = myUsers!!.get(who.baseRef())
                if (prev != null && !who.isEphemeral) {
                    tr!!.eventi("expelling " + prev + " from " + this +
                            " due to reentry as " + who)
                    prev.send(msgExit(this, "duplicate entry", "dupentry",
                            false))
                    prev.forceDisconnect()
                }
                myUsers!![who.baseRef()] = who
            }
            sendContextDescription(who, assertActivated { it.session() })
            noteUserArrival(who)
            tr!!.eventi("$who enters $this")
            null
        }
    }

    /**
     * Remove a user from the context.
     *
     * @param who  The user to remove.
     */
    fun exitContext(who: User) {
        tr!!.eventi("$who exits $this")
        if (myUsers != null && myUsers!!.get(who.baseRef()) != null && myUsers!!.get(who.baseRef())!!.ref() == who.ref()) {
            myUsers!!.remove(who.baseRef())
        }
        if (who.isArrived) {
            if (!isSemiPrivate) {
                send(msgDelete(who))
            }
            noteUserDeparture(who)
        }
        --myUserCount
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
        val members: List<Deliverer> = LinkedList(myGroup!!.members())
        for (member in members) {
            val user = member as User
            user.exitContext("context closing", "contextclose", dup)
        }
    }

    /**
     * Obtain a string describing the reason this context's gate is closed.
     *
     * @return a reason string for this context's gate closure, or null if the
     * gate is open.
     */
    fun gateClosedReason(): String? {
        return myGateClosedReason
    }

    /**
     * Test if this context's gate is closed.  If the gate is closed, new users
     * may not enter, even if the context is not full.
     *
     * @return true iff this context's gate is closed.
     */
    fun gateIsClosed(): Boolean {
        return myGateClosedReason != null
    }

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
            assertActivated { it[ref] as BasicObject }
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
    fun getStaticObject(ref: String): Any? {
        return assertActivated { it.getStaticObject(ref) }
    }

    /**
     * Obtain this context's send group.
     *
     * @return the send group for this context.
     */
    fun group(): SendGroup? = myGroup

    /**
     * Test if this context may be used as a template for other contexts.
     *
     * @return true iff this context is an allowable template context.
     */
    val isAllowableTemplate: Boolean
        get() = amAllowableTemplate || isMandatoryTemplate

    /**
     * Obtain the ref of the context descriptor from which this context was
     * loaded.
     */
    fun loadedFromRef(): String? {
        return myLoadedFromRef
    }

    /**
     * Obtain the number of users who may enter before no more are allowed in.
     *
     * @return the number of users who may enter before the context becomes
     * full.
     */
    fun maxCapacity(): Int {
        return myMaxCapacity
    }

    /**
     * Notify anybody who has expressed an interest in this context shutting
     * down.
     */
    private fun noteContextShutdown() {
        if (myContextShutdownWatchers != null) {
            for (watcher in myContextShutdownWatchers!!) {
                watcher.noteContextShutdown()
            }
        }
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
    fun observePresenceChange(observerRef: String, domain: String?,
                              whoRef: String, whereRef: String, on: Boolean) {
        myPresenceWatcher?.notePresenceChange(observerRef, domain, whoRef, whereRef, on)
        val observer = myUsers!![observerRef]
        if (observer != null) {
            observer.observePresenceChange(observerRef, domain, whoRef,
                    whereRef, on)
        } else {
            tr!!.warningi("presence change of " + whoRef +
                    (if (on) " entering " else " exiting ") + whereRef +
                    " for context " + ref() +
                    " directed to unknown user " + observerRef)
        }
    }

    /**
     * Obtain the director who opened this context.
     *
     * @return the director who asked for this context to be opened.
     */
    fun opener(): DirectorActor? {
        return myOpener
    }

    /**
     * Open this context's gate, allowing new users in if the context is not
     * full.
     */
    fun openGate() {
        myGateClosedReason = null
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
        if (myContextShutdownWatchers == null) {
            myContextShutdownWatchers = LinkedList()
        }
        myContextShutdownWatchers!!.add(watcher)
    }

    /**
     * Register a callback to be invoked when a user enters or exits the
     * context.  Any number of such callbacks may be registered.
     *
     * @param watcher  An object to notify when a user arrives.
     */
    fun registerUserWatcher(watcher: UserWatcher) {
        if (myUserWatchers == null) {
            myUserWatchers = LinkedList()
        }
        myUserWatchers!!.add(watcher)
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
        timer!!.after(millis, ContextEventThunk(thunk))
    }

    /**
     * Class to hold onto a context event thunk so that when the timer triggers
     * the event, the thunk is executed on the server run queue and the context
     * is then released.
     */
    private inner class ContextEventThunk internal constructor(private val myThunk: Runnable) : Runnable, TimeoutNoticer {
        override fun noticeTimeout() {
            assertActivated { it.server().enqueue(this) }
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
    override fun sendObjectDescription(to: Deliverer?, maker: Referenceable) {
        sendContextDescription(to, maker)
    }

    /**
     * Transmit a description of this context as a series of "make" messages.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address message to.
     */
    private fun sendContextDescription(to: Deliverer?, maker: Referenceable) {
        var sess: String? = null
        if (to is User) {
            sess = to.sess()
        }
        to!!.send(msgMake(maker, this, sess))
        if (!isSemiPrivate) {
            for (member in myGroup!!.members()) {
                if (member is User) {
                    member.sendUserDescription(to, this, false)
                }
            }
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
    fun sendToNeighbors(exclude: Deliverer?, message: JSONLiteral?) {
        myGroup!!.sendToNeighbors(exclude!!, message!!)
    }

    fun subscriptions(): Array<String>? {
        return mySubscriptions
    }

    /**
     * Obtain a Deliverer that will deliver to all of a user's neighbors in
     * this context.
     *
     * @param exclude  Who to exclude from the send operation.
     *
     * @return a Deliverer that wraps Context.sendToNeighbors
     */
    fun neighbors(exclude: Deliverer?): Deliverer {
        return object : Deliverer {
            override fun send(message: JSONLiteral) {
                sendToNeighbors(exclude, message)
            }
        }
    }

    /**
     * Obtain a printable string representation of this context.
     *
     * @return a printable representation of this context.
     */
    override fun toString() = "Context '${ref()}'"

    /**
     * Obtain a trace object for logging.
     *
     * @return a trace object for generating log messages from this context.
     */
    fun trace() = tr

    /**
     * Get the number of users in this context.
     *
     * @return the number of users currently in this context.
     */
    fun userCount() = myUserCount

    private inner class UserIterator internal constructor() : MutableIterator<User> {
        private val myInnerIterator: Iterator<Deliverer>
        private var myNext: User?
        override fun hasNext(): Boolean {
            return myNext != null
        }

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
            myInnerIterator = myGroup!!.members().iterator()
            myNext = nextUser
        }
    }

    /**
     * Obtain an iterator over the users currently in this context.  Note that
     * this iterator is only valid within a single turn.
     *
     * @return an @{link java.util.Iterator} over this context's current users.
     */
    fun userIterator(): Iterator<User> {
        return UserIterator()
    }

    /**
     * Handle the 'exit' verb.
     *
     * Exit the context and disconnect the user who sent it.
     */
    @JSONMethod
    @Throws(MessageHandlerException::class)
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
    override fun context() = this

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
    override fun type(): String? {
        return "context"
    }

    /**
     * Obtain the user this object is currently contained by.
     *
     * @return null, since a context is never contained by a user.
     */
    override fun user() = null
    /* ----- Deliverer interface ------------------------------------------- */
    /**
     * Send a message to everyone in this context.  The message will be
     * delivered to every client whose user is currently in the context.
     *
     * @param message  The message to send.
     */
    override fun send(message: JSONLiteral) {
        myGroup!!.send(message)
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
    override fun encode(control: EncodeControl): JSONLiteral {
        val result = JSONLiteralFactory.type("context", control)
        if (control.toClient()) {
            result.addParameter("ref", myRef)
        } else {
            result.addParameter("capacity", myMaxCapacity)
            if (myBaseCapacity != -1) {
                result.addParameter("basecapacity", myBaseCapacity)
            }
            if (isSemiPrivate) {
                result.addParameter("semiprivate", isSemiPrivate)
            }
            if (isRestricted) {
                result.addParameter("restricted", isRestricted)
            }
            if (amContentAgnostic) {
                result.addParameter("agnostic", amContentAgnostic)
            }
            if (amAllowableTemplate) {
                result.addParameter("template", true)
            }
            if (isMandatoryTemplate) {
                result.addParameter("templateonly", true)
            }
            if (myUsers == null) {
                result.addParameter("multientry", true)
            }
            if (mySubscriptions != null) {
                result.addParameter("subscribe", mySubscriptions)
            }
        }
        result.addParameter("name", myName)
        val mods = myModSet.encode(control)
        if (mods.size() > 0) {
            result.addParameter("mods", mods)
        }
        if (control.toRepository() && myUserMods != null) {
            val userMods = JSONLiteralArray(control)
            for (mod in myUserMods) {
                userMods.addElement(mod)
            }
            if (userMods.size() > 0) {
                result.addParameter("usermods", userMods)
            }
        }
        result.finish()
        return result
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
                override fun send(message: JSONLiteral) {
                    for (to in toList) {
                        val toUser = to as User
                        toUser.send(message)
                    }
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
                override fun send(message: JSONLiteral) {
                    for (to in toList) {
                        val toUser = to as User
                        if (toUser != exclude) {
                            toUser.send(message)
                        }
                    }
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
