package org.elkoserver.server.context

import org.elkoserver.foundation.json.DefaultDispatchTarget
import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.json.DispatchTarget
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.MessageRetargeter
import org.elkoserver.json.Encodable
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JsonObject
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.Contents.Companion.withContents
import org.elkoserver.server.context.Contents.Companion.withoutContents
import org.elkoserver.server.context.Contextor.Companion.extractBaseRef
import org.elkoserver.server.context.ModSet.Companion.withMod
import java.util.LinkedList
import java.util.function.Consumer

/**
 * Base class of the fundamental addressable objects in the Context Server:
 * context, user, and item.
 *
 *
 * All such objects share many characteristics in common, hence this base
 * class.  They are all [Encodable] since representations of them can be
 * sent to the client, [DispatchTarget]s since the client can address
 * messages to them, [MessageRetargeter]s since they can have [ ]s attached and so need to be able to retarget arriving messages to those
 * mods, and [Referenceable] since they can be referred to in messages.
 *
 * @param myName  The name of the object (mainly for diagnostic messages).
 * @param mods  Array of mods to attach to the object; can be null if no
 *    mods are to be attached at initial creation time.
 * @param isContainer  true if this object is allowed to be a container.
 * @param contents  Array of inactive items that will be the initial
 *    contents of this object, or null if there are no contents now.
 */
abstract class BasicObject internal constructor(
        var myName: String?, mods: Array<Mod>?, isContainer: Boolean, contents: Array<Item>?) : DefaultDispatchTarget, DispatchTarget, Encodable, MessageRetargeter, Referenceable {
    /** Flag that this object needs to be checkpointed to the database.  */
    private var amChanged = false

    /** Flag that this object has been deleted.  */
    private var amDeleted = false

    /**
     * Test if this object is ephemeral.  If an object is ephemeral, its state
     * is not persisted.  An object is made ephemeral by calling the [ ][.markAsEphemeral] method.
     *
     * @return true if the object is ephemeral, false if not.
     */
    /** Flag that this object is ephemeral.  */
    var isEphemeral = false
        private set

    /** Other objects that should be checkpointed when this object is.  */
    private var myCodependents: MutableList<BasicObject>? = null
    /* Note: various fields below are marked as 'protected' with the keyword
       commented out.  This is because they really want to be both protected
       and package scoped, but Java doesn't have that -- it has to be one or
       the other.  The tie breaker was JavaDoc: it will put protected fields
       into the JavaDoc output, which would be bad because these are not part
       of the official interface. */
    /** Reference string for this object.  */ /* protected */
    @JvmField
    var myRef: String? = null

    /** Objects contained by this object.  */ /* protected */
    @JvmField
    var myContents: Contents? = null

    /** Mods attached to this object.  */ /* protected */
    @JvmField
    var myModSet: ModSet

    /** The contextor for this server.  */ /* protected */
    @JvmField
    var myContextor: Contextor? = null

    /** Optional handler for messages that don't have handlers.  */
    private var myDefaultDispatchTarget: DefaultDispatchTarget? = null

    /** Optional watcher for contents updates.  */
    private var myContentsWatcher: ContentsWatcher? = null

    /** Indicator of visibility rules to apply to this object.  */
    private var myVisibility = VIS_DEFAULT

    /** Count of outstanding initialization events pending before object is
     * considered fully loaded.  */
    private var myUnfinishedInitCount = 0

    /** Inactive content items, prior to activating this object.  */
    private var myPassiveContents: Array<Item>?
    fun addPassiveContents(contents: Array<Item>) {
        myPassiveContents = myPassiveContents?.let { arrayOf(*it, *contents) } ?: contents
    }

    fun notePendingInit() {
        ++myUnfinishedInitCount
    }

    val isReady: Boolean
        get() = myUnfinishedInitCount <= 0

    fun resolvePendingInit() {
        --myUnfinishedInitCount
        if (myUnfinishedInitCount <= 0) {
            assertActivated { it.resolvePendingInit(this)  }
        }
    }

    /**
     * Make this object live inside the context server.
     * @param ref  Reference string identifying this object.
     * @param subID  Clone sub identity, or the empty string for non-clones.
     * @param isEphemeral  True if this object is ephemeral (won't checkpoint).
     * @param contextor  The contextor for this server.
     */
    fun activate(ref: String, subID: String, isEphemeral: Boolean, contextor: Contextor) {
        myRef = ref
        if (isEphemeral) {
            markAsEphemeral()
        }
        myContextor = contextor
        contextor.addRef(this)
        myModSet.attachTo(this)
        activatePassiveContents(subID)
    }

    /**
     * Move contents items from the passive contents array to the actual live
     * contents array and make them live too.
     *
     * @param subID  Clone sub identity, or the empty string for non-clones.
     */
    fun activatePassiveContents(subID: String) {
        assertActivated {
            it.setContents(this, subID, myPassiveContents)
            myPassiveContents = null
        }
    }

    /**
     * Add an item to this object's contents.
     *
     * @param item  The item to add.
     */
    fun addToContents(item: Item) {
        myContents = withContents(myContents, item)
        myContentsWatcher?.noteContentsAddition(item)
    }

    /**
     * Add a new mod to this.
     *
     * @param mod  The mod to attach.
     */
    open fun attachMod(mod: Mod) {
        myModSet = withMod(myModSet, mod)
        myContextor?.addClass(mod.javaClass)
        if (mod is DefaultDispatchTarget) {
            if (myDefaultDispatchTarget == null) {
                myDefaultDispatchTarget = mod
            } else {
                context().trace()!!.errorm("DefaultDispatchTarget mod " + mod +
                        " added to " + this + ", which already has one")
            }
        }
        if (mod is ContentsWatcher) {
            if (myContentsWatcher == null) {
                myContentsWatcher = mod
            } else {
                context().trace()!!.errorm("ContentsWatcher mod " + mod +
                        " added to " + this + ", which already has one")
            }
        }
    }

    fun detachMod(mod: Mod) {
        myModSet.removeMod(mod)
    }

    /**
     * Obtain an object clone's base reference.  This is the object reference
     * string with the clone sub-ID stripped off.
     *
     * @return the base reference string for this object, if it is a clone.  If
     * it is not a clone, the reference string itself will be returned.
     */
    fun baseRef(): String {
        return extractBaseRef(myRef!!)
    }

    /**
     * Checkpoint this object, its contents, and any registered codependent
     * objects (that is, objects whose state must be kept consistent with this
     * object and vice versa).  In other words, ensure that any changes to the
     * aforementioned objects' states that have been made since the last time
     * they were checkpointed are saved to persistent storage.
     */
    fun checkpoint() {
        checkpoint(null)
    }

    /**
     * Checkpoint this object, with completion handler.
     *
     * Note that the completion handler is run when the write of the object
     * itself completes; execution of the completion handler does not indicate
     * that the object's contents or codedependent objects have yet been
     * written!
     *
     * @param handler  Optional completion handler.
     */
    fun checkpoint(handler: Consumer<Any?>?) {
        if (!isEphemeral) {
            checkpointSelf(handler)
            myCodependents?.let {
                val codependents: List<BasicObject> = it
                myCodependents = null
                for (codep in codependents) {
                    codep.checkpoint()
                }
            }
        } else {
            handler?.accept(null)
        }
    }

    /**
     * Write this object and all of its contents (recursively) to the object
     * database if they have changed.
     *
     * @param handler  Optional completion handler.
     */
    internal fun checkpointSelf(handler: Consumer<Any?>?) {
        if (!isEphemeral) {
            myContents?.let {
                for (item in it) {
                    item.checkpointSelf(null)
                }
            }
            doCheckpoint(handler)
        } else {
            handler?.accept(null)
        }
    }

    /**
     * Checkpoint this object and any registered codependent objects.  However,
     * don't bother to checkpoint the objects' contents.
     *
     * This is an optimization used when checkpointing the complete collection
     * of objects in a context.  In such a case, the various objects' contents
     * trees do not need to be walked, since the initiator of the checkpoint
     * will be visiting them all anyway.  However, codependent objects *do*
     * need to be visited since they might not be in the same context.
     */
    fun checkpointWithoutContents() {
        if (!isEphemeral) {
            doCheckpoint(null)
            myCodependents?.let {
                for (codep in it) {
                    codep.checkpoint()
                }
                myCodependents = null
            }
        }
    }

    /**
     * Get this objects's container.  For objects not currently in any
     * container (including non-containable objects), this will be null.  The
     * base case is that the object is not containable (contexts and users are
     * never containable, while items may be), so this base implementation will
     * always return null.
     *
     * @return the object this object is currently contained by.
     */
    open fun container(): BasicObject? {
        return null
    }

    /**
     * Obtain an iterable for this object's contents.  If the object has no
     * contents, either because it is empty or because it is not a container,
     * the iterable returned will be empty (i.e., its iterator's [ ][java.util.Iterator.hasNext] method will return false right
     * away) but null will never be returned.
     *
     * @return an iterable that iterates over this object's contents.
     */
    @Suppress("UNCHECKED_CAST")
    fun contents(): Iterable<Item> = myContents ?: emptyList()

    /**
     * Obtain the context in which this object is located, regardless of how
     * deeply nested in containers it might be.
     *
     * @return the context in which this object is located, at whatever level
     * of container nesting, or null if it is not in any context.
     */
    abstract fun context(): Context

    /**
     * Obtain the contextor that created this object.
     *
     * @return the contextor associated with this object.
     */
    fun contextor() = assertActivated { it }

    /**
     * Create a [Item] directly (i.e., create it at runtime rather than
     * loading it from the database).  The new item will be contained by this
     * object and have neither any contents nor any mods.
     *
     * @param name  The name for the new item, or null if the name doesn't
     * matter.
     * @param isPossibleContainer  true if the new item may itself be used as a
     * container, false if not.
     * @param isDeletable  true if users may delete the new item at will, false
     * if not.
     *
     * @return a new [Item] object as described by the parameters.
     */
    fun createItem(name: String, isPossibleContainer: Boolean, isDeletable: Boolean) =
            assertActivated { it.createItem(name, this, isPossibleContainer, isDeletable) }

    /**
     * Do the actual work of writing this object's changed state to the object
     * database, if its state has actually changed.
     *
     * @param handler  Optional completion handler
     */
    private fun doCheckpoint(handler: Consumer<Any?>?) {
        if (amChanged) {
            amChanged = false
            assertActivated {
                if (amDeleted) {
                    it.writeObjectDelete(baseRef(), handler)
                } else {
                    it.writeObjectState(baseRef(), this, handler)
                }
            }
        } else {
            handler?.accept(null)
        }
    }

    /**
     * Remove this object's contents (and their contents, recursively) from
     * the working set of objects in memory.
     */
    fun dropContents() {
        for (item in contents()) {
            item.dropContents()
            assertActivated { it.remove(item) }
        }
        myContents = null
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * on an object is taking place in the same context as the object.
     *
     * @param who  The user who is attempting the operation.
     *
     * @throws MessageHandlerException if the test fails.
     */
    @Throws(MessageHandlerException::class)
    fun ensureSameContext(who: User) {
        if (context() !== who.context()) {
            throw MessageHandlerException("user " + who +
                    " attempted operation on " +
                    this + " outside own context")
        }
    }

    /**
     * Obtain one of this object's [Mod]s.
     *
     * @param type  The type of the mod desired.
     *
     * @return the mod of the given type, if there is one, else null.
     */
    fun <TMod> getMod(type: Class<TMod>): TMod? {
        return myModSet.getMod(type)
    }

    /**
     * Obtain the user or context holding this object, regardless of how deeply
     * nested in containers it might be.  The base case is that this object is
     * not held.
     *
     * @return the user or context within which this object is contained, at
     * whatever level of container nesting, or null if it is not held by
     * anything.
     */
    open fun holder(): BasicObject? {
        return null
    }

    /**
     * Test if this object is a clone.
     *
     * @return true if this object is a clone object, false if not.
     */
    val isClone: Boolean
        get() = myRef!!.indexOf('-') != myRef!!.lastIndexOf('-')

    /**
     * Test if this object is allowed to be used as a container.
     *
     * @return true if this object can contain other objects, false if not.
     */
    open val isContainer: Boolean
        get() = myContents != Contents.theVoidContents

    /**
     * Mark this object as needing to be written to persistent storage.  Its
     * state and the state of any codependent objects will be written out the
     * next time the object is checkpointed.
     */
    fun markAsChanged() {
        amChanged = true
    }

    /**
     * Mark this object as having been deleted.  Note that unlike [ ][.markAsChanged], there is no corresponding method to unmark deletion;
     * this is a one-way trip.
     */
    fun markAsDeleted() {
        amChanged = true
        amDeleted = true
    }

    /**
     * Mark this object as being ephemeral.
     */
    fun markAsEphemeral() {
        isEphemeral = true
    }

    /**
     * Obtain this object's name, if it has one.
     *
     * @return this object's name, or null if it is nameless.
     */
    fun name() = myName

    /**
     * Note another object that needs to be checkpointed when this object is
     * checkpointed (in order to maintain data consistency).  An object may
     * have any number of codependents.
     *
     * @param obj  The other, codependent object.
     */
    fun noteCodependent(obj: BasicObject) {
        if (myCodependents == null) {
            myCodependents = LinkedList()
        }
        myCodependents!!.add(obj)
    }

    /**
     * Inform this object that its construction is now complete.  This will in
     * turn inform any [Mod]s that have expressed an interest in this
     * event so that they can do any post-creation cleanup or initialization.
     *
     *
     * Application code should not normally need to call this method, since
     * it is called automatically when an object is loaded from persistent
     * storage.  However, certain specialized applications that synthesize
     * objects directly will need to call this after they finish attaching any
     * synthesized [Mod]s.
     */
    fun objectIsComplete() {
        myModSet.objectIsComplete()
    }

    /**
     * Remove an item from this object's contents
     *
     * Note: this method is for use by the implementation of the containership
     * mechanism and should never be called directly.  Instead, use
     * item.setContainer().
     *
     * @param item  The item to remove
     */
    fun removeFromContents(item: Item) {
        myContentsWatcher?.noteContentsRemoval(item)
        myContents = withoutContents(myContents, item)
    }

    /**
     * Transmit a description of this object as a series of 'make' messages,
     * such that the receiver will be able to construct a local presence of it.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address the message(s) to.
     */
    abstract fun sendObjectDescription(to: Deliverer?, maker: Referenceable)

    /**
     * Send a JSON message to all other clones of this object.  The message
     * will be delivered and dispatched to each clone as if it had been
     * received from a client (except that the 'from' parameter will be
     * null).
     *
     * If this Context Server is connected to a Director, the message will be
     * relayed to the Director that will relay it in turn to any clones of this
     * object that reside on other Context Servers connected to that Director.
     * Note that this can be a very expensive operation if used injudiciously.
     *
     * This operation is only valid for users and contexts; it does not work on
     * items.  Also, if this object is not a clone, this method will have no
     * effect.
     *
     * @param message  The message to send.
     */
    fun sendToClones(message: JSONLiteral) {
        assertActivated {  it.relay(this, message) }
    }

    /**
     * Set this object's name.
     *
     * @param name  The new name for the object to have.
     */
    fun setName(name: String) {
        myName = name
        markAsChanged()
    }

    /**
     * Set this object's visibility without taking any other action.  This can
     * only be done once.
     *
     * An object's visibility determines the circumstances under which a
     * description of the object will be transmitted to clients as part of the
     * description of the containing context.  Four cases are supported:
     *
     * [.VIS_PUBLIC] indicates that the object is visible to anyone in
     * the context, i.e., its description will always be transmitted.
     *
     * [.VIS_PERSONAL] indicates that the object's description will only
     * be sent to the user who is holding it and not to anyone else.
     *
     * [.VIS_NONE] indicates that the object's description will never be
     * sent to anyone.
     *
     * [.VIS_CONTAINER] indicates that the object inherits its visiblity
     * from its container, i.e., its description will be transmitted to anyone
     * to whom its container's description is transmitted.
     *
     * If the visibility is not set by calling this method, a default
     * visibility rule will be applied, which is equivalent in every way to
     * [.VIS_PUBLIC] except that it may be modified by calling this
     * method.  Note also that users and contexts are always implicitly set to
     * [.VIS_PUBLIC] regardless.
     *
     * @param visibility  The visibility setting.  This should be one of the
     * constants [.VIS_PUBLIC], [.VIS_PERSONAL], [    ][.VIS_NONE], or [.VIS_CONTAINER].
     */
    fun setVisibility(visibility: Int) {
        if (myVisibility == VIS_DEFAULT) {
            myVisibility = visibility
        } else if (myVisibility != visibility) {
            throw Error("duplicate visibility setting")
        }
    }

    /**
     * Return the proper type tag for this object.
     *
     * @return a type tag string for this kind of object.
     */
    abstract fun type(): String?

    /**
     * Obtain the user within which this object is contained, regardless of how
     * deeply nested in containers it might be.
     *
     * @return the user in which this object is contained, at whatever level of
     * container nesting, or null if it is not contained by a user.
     */
    abstract fun user(): User?

    /**
     * Test if this object is visible to a given receiver.  If the receiver is
     * a [User], then this will test the visibility of the object to that
     * particular user.  If the receiver is a [Context], then it will
     * test the visibility of the object to any user in that context.
     *
     * @param receiver  User or context whose sightlines are at issue.
     *
     * @return true if this object is visible to 'receiver', false if not.
     */
    fun visibleTo(receiver: Deliverer): Boolean {
        return when (myVisibility) {
            VIS_PUBLIC, VIS_DEFAULT -> true
            VIS_PERSONAL -> user() == receiver
            VIS_CONTAINER -> {
                val cont = container()
                cont?.visibleTo(receiver) ?: /* If there is no container, then this is a user or a
                       context and thus visible. */
                true
            }
            VIS_NONE -> false
            else -> throw Error("invalid visibility value in " + this + ": " +
                    myVisibility)
        }
    }
    /* ----- DefaultDispatchTarget interface ------------------------------- */
    /**
     * Handle an otherwise unhandled message.
     *
     * @param from  Who sent the message.
     * @param msg  The message itself.
     *
     * @throws MessageHandlerException if there was a problem handling the
     * message.
     */
    override fun handleMessage(from: Deliverer, msg: JsonObject) {
        myDefaultDispatchTarget?.handleMessage(from, msg)
                ?: throw MessageHandlerException("no message handler method for verb '${msg.getString("op", null)}'")
    }
    /* ----- MessageRetargeter interface ---------------------------------- */
    /**
     * Find the object to handle a message for some class (either the object
     * itself or one of its mods).  This method is part of the message
     * handling subsystem; applications will not normally have need to call it.
     *
     * @param type  The class associated with the message verb.
     *
     * @return an object that can handle messages for class 'type', or null if
     * this object doesn't handle messages for that class.
     */
    override fun <TTarget : DispatchTarget> findActualTarget(type: Class<TTarget>): TTarget? =
            if (type == javaClass) {
                @Suppress("UNCHECKED_CAST")
                this as TTarget
            } else {
                myModSet.getMod(type) as TTarget?
            }
    /* ----- Referenceable interface --------------------------------------- */
    /**
     * Obtain this object's reference string.
     *
     * @return a string that can be used to refer to this object in JSON
     * messages, either as the message target or as a parameter value.
     */
    override fun ref(): String {
        return myRef!!
    }

    companion object {
        /** Visibility has not been set.  */
        private const val VIS_DEFAULT = 0

        /** Visible to everyone in the enclosing context.  */
        private const val VIS_PUBLIC = 1

        /** Visible to user holding object but nobody else.  */
        const val VIS_PERSONAL = 2

        /** Not visible to anyone.  */
        const val VIS_NONE = 3

        /** Visible according to the visibility of its container.  */
        private const val VIS_CONTAINER = 4
    }

    init {
        myContents = if (isContainer || contents != null) {
            null
        } else {
            Contents.theVoidContents
        }
        myPassiveContents = contents
        myModSet = ModSet(mods)
    }

    protected fun <TResult> assertActivated(myContextorConsumer: (Contextor) -> TResult) =
            myContextor?.let(myContextorConsumer) ?: throw IllegalStateException("Not activated")
}
