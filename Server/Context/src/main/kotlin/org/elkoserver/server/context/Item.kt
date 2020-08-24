package org.elkoserver.server.context

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable
import java.util.LinkedList

/**
 * A Item is an application object contained by a context or a user (or another
 * Item) but which is not a context or user itself.  Along with [Context]
 * and [User] it is one of the three basic object types.
 */
class Item : BasicObject {
    /**
     * Test if unprivileged users inside the context can delete this item (by
     * sending a 'delete' message to the server).
     *
     * @return  true if ordinary users can delete this item, false if not.
     */
    /** Flag that users may delete this item.  */
    private val isDeletable: Boolean

    /**
     * Test if unprivileged users inside the context can move this item (by
     * sending a 'move' message to the server).
     *
     * @return  true if ordinary users can move this item, false if not.
     */
    /** Flag that users may move this item around.  */
    var isPortable: Boolean = false
        private set

    /**
     * Test if this is a closed container.
     *
     * @return  true if this item is a container that is closed.
     */
    /** Flag this container item is closed.  */
    var isClosed: Boolean
        private set
    /* Fields below here only apply to active items. */
    /** Object that contains this item.  */
    private var myContainer: BasicObject? = null

    /** Optional watcher for container updates.  */
    private var myContainerWatcher: ContainerWatcher? = null

    /**
     * Manual item constructor.  Items constructed via this method are created
     * with no mods and no contents.
     *  @param name  Item name.
     * @param isContainer  Flag indicating whether the item may be used as a
     *    container.
     * @param isDeletable  Flag indicating whether users my delete this item.
     * @param isClosed  Flag indicating whether this container is closed.
     */
    internal constructor(name: String, isContainer: Boolean, isDeletable: Boolean,
                         isClosed: Boolean) : super(name, null, isContainer, null) {
        this.isDeletable = isDeletable
        this.isClosed = isClosed
    }

    /**
     * JSON-driven constructor.
     * @param name  The name of the item.
     * @param ref  Nominal reference string for the item (may be overridden).
     * @param mods  Array of mods to attach to the user; can be null if no mods
     * are to be attached at initial creation time.
     * @param contents  Array of inactive items that will be the initial
     * contents of this user, or null if there are no contents now.
     * @param in  Optional ref of container holding this item.
     * @param isPossibleContainer  Flag indicating whether the item may be used
     * as a container.
     * @param isDeletable  Flag indicating whether users may delete this item.
     * @param isPortable  Flag indicating whether users may move this item.
     * @param isClosed  Flag indicating whether this container is closed.
     */
    @JsonMethod("name", "ref", "mods", "contents", "in", "cont", "deletable", "portable", "closed")
    internal constructor(name: String, ref: OptString, mods: Array<Mod>, contents: Array<Item>, `in`: OptString,
                         isPossibleContainer: OptBoolean, isDeletable: OptBoolean,
                         isPortable: OptBoolean, isClosed: OptBoolean) : super(name, mods, isPossibleContainer.value(true), contents) {
        myRef = ref.value<String?>(null)
        this.isDeletable = isDeletable.value(false)
        this.isPortable = isPortable.value(false)
        this.isClosed = isClosed.value(false)
    }

    /**
     * Add a new mod to this item.  The mod must be an [ItemMod] even
     * though the method is declared generically.  If it is not, it will not be
     * added, and an error message will be written to the log.
     *
     * @param mod  The mod to attach; must be an [ItemMod].
     */
    override fun attachMod(mod: Mod) {
        if (mod is ItemMod) {
            super.attachMod(mod)
        } else {
            myGorgel.error("attempt to attach non-ItemMod $mod")
        }
        if (mod is ContainerWatcher) {
            if (myContainerWatcher == null) {
                myContainerWatcher = mod
            } else {
                myGorgel.error("ContainerWatcher mod $mod added, which already has one")
            }
        }
    }

    /**
     * If this item is an open container, close it.  This results in the
     * broadcast of "delete" messages to the context for all the item's
     * contents (assuming it has any).
     *
     * If the item is not a container or is already closed, this is a no-op.
     */
    fun closeContainer() {
        if (isContainer && !isClosed) {
            isClosed = true
            markAsChanged()
            for (item in contents()) {
                context().send(msgDelete(item))
            }
            dropContents()
        }
    }

    /**
     * Obtain this item's container.
     *
     * @return the object this item is currently contained by.
     */
    override fun container(): BasicObject? = myContainer

    /**
     * Obtain the context in which this item is located, regardless of how
     * deeply nested in containers it might be.
     *
     * @return the context in which this item is located, at whatever level of
     * container nesting, or null if it is not in any context.
     */
    override fun context(): Context = assertInContainer(BasicObject::context)

    /**
     * Delete this item (and, by implication, its contents).  The caller is
     * responsible for notifying any clients who need to know that this has
     * happened.
     */
    fun delete() {
        /* copy contents list to avoid concurrent modification problems */
        val copy: MutableList<Item> = LinkedList()
        copy += contents()
        for (item in copy) {
            item.delete()
        }
        setContainer(null)
        markAsDeleted()
        assertActivated { it.remove(this) }
    }

    private fun baseEncode(result: JsonLiteral, control: EncodeControl) {
        result.run {
            addParameter("ref", myRef)
            addParameterOpt("name", name)
            val mods = myModSet.encode(control)
            if (mods.size > 0) {
                addParameter("mods", mods)
            }
            if (!isContainer) {
                addParameter("cont", false)
            } else {
                if (isClosed) {
                    addParameter("closed", true)
                }
            }
            if (isPortable) {
                addParameter("portable", true)
            }
            if (control.toRepository()) {
                myContainer?.let { addParameter("in", it.baseRef()) }
                if (isDeletable) {
                    addParameter("deletable", true)
                }
            }
        }
    }

    /**
     * Encode this item for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this item.
     */
    override fun encode(control: EncodeControl): JsonLiteral {
        val result = JsonLiteralFactory.type("item", control)
        baseEncode(result, control)
        result.finish()
        return result
    }

    /**
     * Obtain the user or context holding this object, regardless of how deeply
     * nested in containers it might be.
     *
     * @return the user or context within which this object is contained, at
     * whatever level of container nesting, or null if it is not held by
     * anything.
     */
    override fun holder(): BasicObject? =
            when (val currentContainer = myContainer) {
                null -> null
                is Item -> currentContainer.holder()
                else -> currentContainer
            }

    /**
     * If this item is a closed container, open it.  This results in the
     * broadcast of "make" messages to the context announcing the item's
     * contents (if it has any).
     *
     * If the item is not a container or is already open, this is a no-op.
     */
    fun openContainer() {
        if (isContainer && isClosed) {
            isClosed = false
            markAsChanged()
            assertActivated {
                it.loadItemContents(this, { obj: Any? ->
                    activatePassiveContents("")
                    it.notifyPendingObjectCompletionWatchers()
                    myContents?.sendContentsDescription(context(), this@Item)
                })
            }
        }
    }

    /**
     * Transmit a description of this item as a series of 'make' messages.
     *
     * This method will generate and send a series of 'make' messages that
     * direct a client or clients to construct a representation of this item
     * and its contents.  If this item is visible to the indicated receiver,
     * one 'make' message will be sent describing this object itself, and then
     * an additional message for each visible object in its descendent
     * containership hierarchy.
     *
     * Application code will not normally need to call this method, since it
     * is invoked automatically as part of the normal transmission of a context
     * and its contents to users arriving in the context.  However, certain
     * specialized applications that synthesize objects directly will need to
     * call this in order to describe what they have created to connected
     * clients.
     *
     * @param to  Where to send the description.  This is the destination to
     * which these messages will be delivered; it will typically be a
     * context or user object.  It is the entity with respect to which this
     * item's visibility or invisibility (and that of its contents) is
     * determined for purposes of deciding whether or not to send 'make'
     * messages.
     * @param maker  Maker object to address message to.  This is the object
     * that is responsible, on the client, for creating the client presence
     * of the item.  Normally this should be the item's container.
     * @param force  If true, force the transmission, even if this item is
     * marked as being invisible to 'to'.  Note, however, that forcing
     * transmission in this manner only overrides the invisibility of this
     * item itself and not that of any other items that it may contain.
     */
    fun sendItemDescription(to: Deliverer, maker: Referenceable, force: Boolean) {
        if (force || visibleTo(to)) {
            to.send(msgMake(maker, this))
            myContents?.sendContentsDescription(to, this)
        }
    }

    /**
     * Transmit a description of this item as a series of 'make' messages,
     * such that the receiver will be able to construct a local presence of it.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address the message(s) to.
     */
    override fun sendObjectDescription(to: Deliverer, maker: Referenceable) {
        sendItemDescription(to, maker, false)
    }

    /**
     * Set or change this item's container.  The participating containers will
     * be marked as having changed, so that the change of containership will be
     * persistent.
     *
     * @param container  The new container for this item, or null to indicate
     * that it should now have no container.
     */
    fun setContainer(container: BasicObject?) {
        val oldContainer = myContainer
        myContainer?.let {
            it.markAsChanged()
            it.noteCodependent(this)
            noteCodependent(it)
            if (container != null) {
                it.noteCodependent(container)
                container.noteCodependent(it)
            }
        }
        setContainerPrim(container)
        myContainer?.let {
            it.markAsChanged()
            it.noteCodependent(this)
            noteCodependent(it)
        }
        myContainerWatcher?.noteContainerChange(oldContainer, container)
    }

    /**
     * Set or change this item's container primitively.  This routine does not
     * fiddle with the changed flags and is for use during object construction
     * only.
     *
     * @param container  The new container for this item, or null to indicate
     * that it should now have no container.
     */
    fun setContainerPrim(container: BasicObject?) {
        myContainer?.removeFromContents(this)
        myContainer = container
        myContainer?.addToContents(this)
    }

    /**
     * Obtain a printable string representation of this item.
     *
     * @return a printable representation of this item.
     */
    override fun toString(): String = "Item '$myRef'"

    /**
     * Return the proper type tag for this object.
     *
     * @return a type tag string for this kind of object; in this case, "item".
     */
    override fun type(): String = "item"

    /**
     * Obtain the user within which this item is contained, regardless of how
     * deeply nested in containers it might be.
     *
     * @return the user in which this item is contained, at whatever level of
     * container nesting, or null if it is not contained by a user.
     */
    override fun user(): User? = myContainer?.user()

    /**
     * Message handler for the 'delete' message.
     *
     * If the item is deletable, the item is deleted and a 'delete' message
     * is broadcast to everyone in the context informing them of this.
     */
    @JsonMethod
    fun delete(from: User) {
        ensureSameContext(from)
        if (isDeletable) {
            from.context().send(msgDelete(this))
            delete()
        }
    }

    private fun <TResult> assertInContainer(containerConsumer: (BasicObject) -> TResult) =
            myContainer?.let(containerConsumer) ?: throw IllegalStateException("Not in a container")
}
