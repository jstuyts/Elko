package org.elkoserver.server.context.model

import org.elkoserver.foundation.json.DispatchTarget
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.json.Encodable

/**
 * Abstract base class to facilitate implementation of application-specific
 * mods that can be attached to basic objects (contexts, users, and items).
 *
 * This class implements basic services needed by or useful to all mods,
 * regardless of application.
 *
 * Subclasses need to implement application-specific mod logic as well as the
 * [encode()][Encodable.encode] method called for by the [Encodable] interface.
 */
abstract class Mod protected constructor() : Encodable, DispatchTarget, Cloneable {
    /** The object to which this Mod is attached, or null if unattached.  */
    private var myObject: BasicObject? = null

    /**
     * Test if this mod is ephemeral.  If a mod is ephemeral, its state is
     * not persisted.  A mod is made ephemeral by calling the [markAsEphemeral] method.
     *
     * @return true if the mod is ephemeral, false if not.
     */
    var isEphemeral: Boolean = false
        private set

    /**
     * Attach this mod to an object.
     *
     * Only one mod of any given class may be attached to any given object.
     *
     * Application code will not normally need to call this method, since it
     * is called automatically when an object is loaded from persistent
     * storage.  However, certain specialized applications that synthesize
     * objects directly will need to use this to attach the mods they have
     * constructed to the objects they have constructed.
     *
     * @param object  The object to which this mod is to be attached.
     */
    fun attachTo(`object`: BasicObject) {
        myObject = `object`
        `object`.attachMod(this)
    }

    fun detach() {
        myObject?.detachMod(this)
        myObject = null
    }

    /**
     * Clone this object.
     */
    public override fun clone(): Any = super.clone()

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * is being applied to an object that that user is allowed to reach (either
     * because it is in the context or because the user is holding it).  If
     * this mod is not attached to such an object, this method will throw a
     * [MessageHandlerException] exception.
     *
     * @param who  The user who is attempting the operation.
     *
     * @throws MessageHandlerException if the test fails.
     */
    protected fun ensureReachable(who: User) {
        assertAttached {
            val holder = it.user()
            if (holder !== who) {
                if (holder == null) {
                    ensureSameContext(who)
                } else {
                    throw MessageHandlerException("user $who attempted operation on non-reachable object $it")
                }
            }
        }
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * is being applied to an object that that user is holding.  If this mod is
     * not attached to such an object, this method will throw a [MessageHandlerException] exception.
     *
     * @param who  The user who is attempting the operation.
     * @throws MessageHandlerException if the test fails.
     */
    protected fun ensureHolding(who: User) {
        assertAttached {
            val holder = it.user()
            if (holder !== who) {
                throw MessageHandlerException("user $who attempted operation on non-held object $it")
            }
        }
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * is being applied to that same user.  If this mod is not attached to
     * that user, this method will throw a [MessageHandlerException].
     *
     * @param who  The user who is attempting the operation.
     *
     * @throws MessageHandlerException if the test fails.
     */
    protected fun ensureSameUser(who: User) {
        assertAttached {
            if (who !== it) {
                throw MessageHandlerException("user $who attempted operation on $it instead of self")
            }
        }
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * on an object is taking place in the same context as the object.  If this
     * mod is not attached to an object in the same context as the user, this
     * method will throw a [MessageHandlerException].
     *
     * @param who  The user who is attempting the operation.
     *
     * @throws MessageHandlerException if the test fails.
     */
    protected fun ensureSameContext(who: User) {
        assertAttached {
            it.ensureSameContext(who)
        }
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * on an object that is contained by the user's context.  If this mod is
     * not attached to such an object, this method will throw a [MessageHandlerException].
     *
     * @param who  The user who is attempting the operation.
     *
     * @throws MessageHandlerException if the test fails.
     */
    protected fun ensureInContext(who: User) {
        assertAttached {
            if (who.context() !== it.container() && who.context() !== it) {
                throw MessageHandlerException("user $who attempted operation on object $it that is not in the user's context")
            }
        }
    }

    /**
     * Obtain the user or context holding the object to which this mod is
     * attached, regardless of how deeply nested in containers it might be.
     *
     * @return the user or context in which the object that mod is attached to
     * is located, at whatever level of container nesting, or null if it is
     * not held by anything.
     */
    protected fun holder(): BasicObject? = assertAttached(BasicObject::holder)

    /**
     * Mark the object to which this mod is attached as having been changed and
     * thus in need of checkpointing.
     */
    protected fun markAsChanged() {
        assertAttached(BasicObject::markAsChanged)
    }

    /**
     * Mark this mod as being ephemeral.
     */
    fun markAsEphemeral() {
        isEphemeral = true
    }

    /**
     * Obtain the object to which this mod is attached.
     *
     * @return the object to which this mod is attached.
     */
    fun `object`(): BasicObject = assertAttached { it }

    /**
     * Obtain the context in which the object this mod is attached to is
     * located, regardless of how deeply nested in containers the object might
     * be.
     *
     * @return the context in which this mod is located, or null if it is not
     * in any context.
     */
    fun context(): Context = assertAttached(BasicObject::context)

    private fun <TResult> assertAttached(myObjectConsumer: (BasicObject) -> TResult) =
        myObject?.let(myObjectConsumer) ?: throw IllegalStateException("Not attached")
}
