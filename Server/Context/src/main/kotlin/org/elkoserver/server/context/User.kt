package org.elkoserver.server.context

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.net.Connection
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.Contents.Companion.sendContentsDescription
import org.elkoserver.server.context.Msg.msgExit
import org.elkoserver.server.context.Msg.msgMake
import org.elkoserver.server.context.Msg.msgReady
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.function.Consumer

/**
 * A User represents a connection to someone entered into a context from a
 * client.  It is one of the three basic object types (along with [ ] and [Item]).
 */
class User(name: String?, mods: Array<Mod>?, contents: Array<Item>?, ref: String?) : BasicObject(name, mods, true, contents), Deliverer {
    /** True once user has actually been placed in its initial context.  */
    var isArrived = false
        private set

    /** Flag indicating that user has completed context entry.  */
    private var amEntered = false

    /** Flag to prevent race in exit where users bump off themselves.  */
    private var amExited = false

    /** Context that user is currently in (or null if not in a context).  */
    private var myContext: Context? = null

    /** Client session ID for this user's connection to the context (null if
     * the client isn't concerned with identifying this).  */
    internal var sess: String? = null

    /* Fields below here only apply to active Users. */

    /** Send group in which this user is currently a member.  */
    private var myGroup: SendGroup = LimboGroup.theLimboGroup

    /** The actor that represents the connection to the client.  */
    private lateinit var myActor: UserActor

    /** Optional watcher for friend presence changes.  */
    private var myPresenceWatcher: PresenceWatcher? = null

    /** Flag that user is an anonymous, ephemeral user.  */
    var isAnonymous = false
        private set

    /** Flag that user contents are opaque to other users.  */
    private val amPrivateContents = false

    /**
     * JSON-driven constructor.
     * @param name  The name of the user.
     * @param mods  Array of mods to attach to the user; can be null if no mods
     * are to be attached at initial creation time.
     * @param contents  Array of inactive items that will be the initial
     * contents of this user, or null if there are no contents now.
     * @param ref  Optional reference string for this user object.
     */
    @JSONMethod("name", "?mods", "?contents", "ref")
    internal constructor(name: OptString, mods: Array<Mod>?, contents: Array<Item>?, ref: OptString) : this(name.value<String?>(null), mods, contents, ref.value<String?>(null))

    /**
     * Activate a user.
     * @param ref  Reference string identifying this user.
     * @param subID  Clone sub identity, or the empty string for non-clones.
     * @param contextor  The contextor for this server.
     * @param name  The (revised) name for this user.
     * @param theSess  Client session ID for this user's connection to their
     * context, or null if the client doesn't care.
     * @param isEphemeral  True if this user is ephemeral (won't checkpoint).
     * @param isAnonymous  True if this user is anonymous
     * @param actor  The actor through which this user communicates.
     */
    fun activate(ref: String, subID: String, contextor: Contextor, name: String,
                 theSess: String?, isEphemeral: Boolean, isAnonymous: Boolean,
                 actor: UserActor, gorgel: Gorgel) {
        super.activate(ref, subID, isEphemeral, contextor, gorgel)
        this.name = name
        sess = theSess
        myActor = actor
        this.isAnonymous = isAnonymous
        amEntered = true
    }

    /**
     * Add a new mod to this user.  The mod must be a [UserMod] even
     * though the method is declared generically.  If it is not, it will not be
     * added, and an error message will be written to the log.
     *
     * @param mod  The mod to attach; must be a [UserMod].
     */
    override fun attachMod(mod: Mod) {
        if (mod is UserMod) {
            super.attachMod(mod)
            if (mod is PresenceWatcher) {
                myPresenceWatcher = mod
            }
        } else {
            myGorgel.error("attempt to attach non-UserMod $mod to $this")
        }
    }

    /**
     * Handle loss of connection from the user.
     * @param connection  The connection that died.
     */
    fun connectionDied(connection: Connection) {
        disconnect()
        myGorgel.i?.run { info("connection died: $connection") }
    }

    /**
     * Obtain this user's connection ID.
     *
     * @return the ID number of the connection associated with this user.
     */
    fun connectionID() = myActor.connectionID()

    /**
     * Obtain the context this user is currently contained by.
     *
     * @return the context the user is in.
     */
    override fun context() = assertInContext { it }

    /**
     * Do the actual work of exiting a user from their context and
     * disconnecting them.
     */
    private fun disconnect() {
        if (!amExited) {
            myGorgel.i?.run { info("exiting") }
            amExited = true
            if (amEntered) {
                checkpoint()
                assertActivated {
                    it.remove(this)
                    it.noteUser(this, false)
                }
                exitCurrentContext()
            }
        }
    }

    /**
     * Encode this user for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this user.
     */
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("user", control).apply {
                if (control.toClient()) {
                    addParameter("ref", myRef)
                }
                addParameter("name", name)
                val mods = myModSet.encode(control)
                if (mods.size > 0) {
                    addParameter("mods", mods)
                }
                finish()
            }

    /**
     * Place this user into a context.  The user will be removed from any
     * previous context first.
     *
     * @param context  The context to enter.
     *
     * @return null if successful, or an error message string if not.
     */
    fun enterContext(context: Context): String? {
        exitCurrentContext()
        myContext = context
        val problem = context.enterContext(this)
        return if (problem == null) {
            isArrived = true
            myGroup.expelMember(this)
            myGroup = context.group
            myGroup.admitMember(this)
            context.attachUserMods(this)
            objectIsComplete()
            assertActivated(Contextor::notifyPendingObjectCompletionWatchers)
            sendUserDescription(this, context, true)
            if (!context.isSemiPrivate) {
                sendUserDescription(neighbors(), context, false)
            }
            null
        } else {
            problem
        }
    }

    /**
     * Test if this user is allowed into an entry restricted context.
     *
     * @param contextRef  Reference string of the context in question.
     *
     * @return true if this user has the key to enter the specified context,
     * false if not.
     */
    fun entryEnabled(contextRef: String) = testForEntryKey(this, contextRef)

    /**
     * Remove this user from their context.
     *
     * @param why  Explanation string to send to this user as the last thing
     * they see from the context.
     * @param whyCode  Machine readable tag encoding 'why'.
     * @param reload  true if the client should attempt a reload (i.e.,
     * immediately try to enter again), false if not.
     */
    fun exitContext(why: String?, whyCode: String?, reload: Boolean) {
        assertInContext { send(msgExit(it, why, whyCode, reload)) }
        disconnect()
    }

    /**
     * Remove the user from the current context.
     */
    private fun exitCurrentContext() {
        myContext?.let {
            myGroup.expelMember(this)
            myGroup = LimboGroup.theLimboGroup
            it.exitContext(this)
            myModSet.purgeEphemeralMods()
            myActor.exitContext(it)
            myContext = null
            isArrived = false
        }
    }

    /**
     * Tell the user to go to a different context, then disconnect them from
     * this one.
     *
     * @param contextRef  The ref of the context they should go to
     * @param hostPort  Host:port string of the context server for the context
     * @param reservation  Reservation to get them in.
     */
    fun exitWithContextChange(contextRef: String, hostPort: String?, reservation: String?) {
        checkpoint(Consumer<Any?> { ignored: Any? ->
            assertActivated { send(msgPushContext(it.session, contextRef, hostPort, reservation)) }
        })
    }

    /**
     * Force this user to actually disconnect from the server.
     */
    fun forceDisconnect() {
        myActor.doDisconnect()
    }

    /**
     * Test if this object is a container.  (Note: in this case, it is).
     *
     * @return true -- all users are containers.
     */
    override val isContainer = true

    /**
     * Obtain a message deliverer for sending messages to the other users
     * in this user's context.
     *
     * @return a deliverer representing this user's current neighbors.
     */
    private fun neighbors(): Deliverer = Neighbors(myGroup, this)

    /**
     * Take notice that a user elsewhere has come or gone.
     *
     * @param observerRef  Ref of user who cares (presumably *this* user)
     * @param domain  Presence domain of relationship between users
     * @param whoRef  Ref of user who came or went
     * @param whereRef  Ref of context the entered or exited
     * @param on  True if they came, false if they left
     */
    fun observePresenceChange(observerRef: String?, domain: String?,
                              whoRef: String?, whereRef: String?, on: Boolean) {
        myPresenceWatcher?.notePresenceChange(observerRef, domain, whoRef, whereRef, on)
    }

    /**
     * Obtain the protocol by which this user is talking to this server.
     *
     * @return the protocol string for this user's connection
     */
    fun protocol() = myActor.protocol

    /**
     * Begin the sequence of events that will push this user to a different
     * context.  This will trigger a reservation request to the director and
     * ultimately a message to the user telling them where to go and how to get
     * in.
     *
     * @param contextRef  The ref of the context to push them to.
     */
    fun pushNewContext(contextRef: String) {
        assertActivated { it.pushNewContext(this, contextRef) }
    }

    /**
     * Send a message to this user.  The message will be delivered on the
     * client connection this user is currently connected by.
     *
     * @param message  The message to send.
     */
    override fun send(message: JSONLiteral) {
        myActor.send(message)
    }

    /**
     * Transmit a description of this user as a series of 'make' messages,
     * such that the receiver will be able to construct a local presence of it.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address the message(s) to.
     */
    override fun sendObjectDescription(to: Deliverer, maker: Referenceable) {
        sendUserDescription(to, maker, false)
    }

    /**
     * Transmit a description of this user as a series of 'make' messages.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address message to.
     * @param you  If true, user description is being sent to the user being
     * described.
     */
    fun sendUserDescription(to: Deliverer, maker: Referenceable, you: Boolean) {
        to.send(msgMake(maker, this, null, you, null))
        if (!amPrivateContents || to === this) {
            sendContentsDescription(to, this, myContents)
        }
        to.send(msgReady(this))
    }

    /**
     * Test if an oject or any of its contents have a context entry key that
     * enables access to a given context.
     *
     * @param obj  The object to test.
     * @param contextRef  The reference string of the context of interest.
     *
     * @return true if 'obj' or any of the objects it contains have an attached
     * [ContextKey] mod that gives access to the context designated by
     * 'contextRef'.
     */
    private fun testForEntryKey(obj: BasicObject, contextRef: String): Boolean {
        val key = obj.getMod(ContextKey::class.java)
        if (key != null && key.enablesEntry(contextRef)) {
            return true
        }
        return obj.contents().any { testForEntryKey(it, contextRef) }
    }

    /**
     * Obtain a printable string representation of this user.
     *
     * @return a printable representation of this user.
     */
    override fun toString() = "User '${ref()}'"

    /**
     * Return the proper type tag for this object.
     *
     * @return a type tag string for this kind of object; in this case, "user".
     */
    override fun type() = "user"

    /**
     * Obtain the user this object is currently associated with.
     *
     * @return the user itself.
     */
    override fun user() = this

    companion object {
        /**
         * Create a 'pushcontext' message.
         *
         * @param target  Object the message is being sent to
         * @param contextRef  Ref of the context to which the user is being sent
         * @param hostPort  Host:port of the context server they should use
         * @param reservation  Reservation code to tender to gain entry
         */
        private fun msgPushContext(target: Referenceable, contextRef: String, hostPort: String?, reservation: String?) =
                JSONLiteralFactory.targetVerb(target, "pushcontext").apply {
                    addParameter("context", contextRef)
                    addParameterOpt("hostport", hostPort)
                    addParameterOpt("reservation", reservation)
                    finish()
                }
    }

    init {
        myRef = ref
        myGroup.admitMember(this)
    }

    private fun <TResult> assertInContext(contextConsumer: (Context) -> TResult) =
            myContext?.let(contextConsumer) ?: throw IllegalStateException("Not in a context")
}
