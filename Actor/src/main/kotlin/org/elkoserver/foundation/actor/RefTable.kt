package org.elkoserver.foundation.actor

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.json.DispatchTarget
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.json.JsonObject
import org.elkoserver.json.Referenceable
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.HashMap
import java.util.LinkedList

/**
 * A mapping from object reference strings (as they would be used in JSON
 * message parameters) to the objects they refer to.  This mapping may be
 * modified at any time by adding or removing objects.  The class also supports
 * the direct invocation (from JSON messages) of JSON methods on the mapped
 * objects.
 *
 * By convention, the object references mapped by this table take the form:
 *
 * <blockquote>*type*-*ref*</blockquote>
 *
 * where: 'type' designates the kind of object being referred to, while
 * 'ref' designates a specific object of that type.  The 'ref' and accompanying
 * hyphen separator are optional (actually, you are not required to follow this
 * convention at all, but various classes provide convenience methods that are
 * helpful if you do).  The specific forms of 'type' and 'ref' themselves are
 * unconstrained (other than the use of a hyphen as separator).
 */
class RefTable(private val myDispatcher: MessageDispatcher, baseCommGorgel: Gorgel) : Iterable<DispatchTarget?> {
    /** Mapped Objects, indexed by reference.  */
    private val myObjects: MutableMap<String?, DispatchTarget> = HashMap()

    /** Collections of objects sharing a common root reference string (used in
     * the object cloning mechanism: clones are generated by suffixing a
     * counter to the reference string of the parent object).  */
    private val myObjectGroups: MutableMap<String?, MutableList<DispatchTarget>> = HashMap()

    /**
     * Internal error handler object that is the target of debug JSON messages.
     * A single instance of this is created and stored in the lookup table when
     * it is created, so that received error and debug messages have someplace
     * to go.
     */
    private class ErrorHandler(commGorgel: Gorgel) : BasicProtocolHandler(commGorgel) {
        /**
         * This object is always called 'error' (there should only ever be one
         * instance).
         *
         * @return a reference string for this object.
         */
        override fun ref() = "error"
    }

    /**
     * Add JSON method dispatch information for a Java class to the table's
     * message dispatcher, independent of any particular object instance.
     *
     * @param targetClass  Java class whose JSON methods are to be added.
     */
    fun addClass(targetClass: Class<*>) {
        myDispatcher.addClass(targetClass)
    }

    /**
     * Add an object to the table, explicitly specifying its reference string.
     *
     * @param ref  The reference string for the object.
     * @param target  The object referenced by the reference string.
     */
    fun addRef(ref: String, target: DispatchTarget) {
        myObjects[ref] = target
        myDispatcher.addClass(target.javaClass)
        val groupRef = rootRef(ref)
        val group = myObjectGroups.computeIfAbsent(groupRef) { LinkedList() }
        group.add(target)
    }

    /**
     * Add an object to the table, using the reference string it knows for
     * itself.
     *
     * @param target  The object referenced by the reference string.
     */
    fun addRef(target: Referenceable) {
        addRef(target.ref(), target as DispatchTarget)
    }

    /**
     * Get a list of all objects in the table that have a common root reference
     * string.
     *
     * @param ref  Reference string designating the object(s) of interest.
     *
     * @return a list of all objects in the table denoted by 'ref'.  If 'ref'
     * is the root reference of a clone group, returns a list of all the
     * clones with that root reference.  If 'ref' designates a non-clone
     * object, returns a single element list containing that unique object.
     * If there are no objects corresponding to 'ref', an empty list is
     * returned.
     */
    fun clones(ref: String?): List<DispatchTarget> {
        val group: List<DispatchTarget>? = myObjectGroups[ref]
        return if (group == null) {
            val `object` = myObjects[ref]
            `object`?.let { listOf(it) } ?: emptyList()
        } else {
            group
        }
    }

    /**
     * Dispatch a JSON message directly to the appropriate method of a given
     * object.
     *
     * @param from  Alleged sender of the message.
     * @param target  The object to which the message is being sent.
     * @param message  The message itself.
     *
     * @throws MessageHandlerException if there was some kind of problem
     * handling the message.
     */
    fun dispatchMessage(from: Deliverer?, target: DispatchTarget, message: JsonObject) {
        myDispatcher.dispatchMessage(from, target, message)
    }

    /**
     * Dispatch a JSON message to the appropriate method on the object that the
     * message says it is addressed to.
     *
     * @param from  Alleged sender of the message.
     * @param message  The message itself.
     *
     * @throws MessageHandlerException if there was some kind of problem
     * handling the message.
     */
    fun dispatchMessage(from: Deliverer, message: JsonObject) {
        val targetRef = message.getString<String?>("to", null)
        if (targetRef != null) {
            val target = get(targetRef)
            if (target != null) {
                myDispatcher.dispatchMessage(from, target, message)
            } else {
                throw MessageHandlerException("target object '$targetRef' not found")
            }
        } else {
            throw MessageHandlerException("no target in message")
        }
    }

    /**
     * Look up an object by reference string.
     *
     * @param ref  Reference string denoting the object sought.
     *
     * @return the object designated by 'ref', or null if there is no such
     * object.
     */
    operator fun get(ref: String?): DispatchTarget? = myObjects[ref]

    /**
     * Support iteration over all objects in the table.
     *
     * @return an [Iterator] over all the objects.
     */
    override fun iterator(): MutableIterator<DispatchTarget> = myObjects.values.iterator()

    /**
     * Remove an object from the table, explicitly specifying its reference
     * string.  It is permissible to specify a reference string that isn't
     * actually there.
     *
     * @param ref  Reference string for the object to be removed.
     */
    private fun remove(ref: String) {
        val `object` = myObjects[ref]
        if (`object` != null) {
            myObjects.remove(ref)
            val groupRef = rootRef(ref)
            val group: MutableList<DispatchTarget>? = myObjectGroups[groupRef]
            if (group != null) {
                group.remove(`object`)
                if (group.isEmpty()) {
                    myObjectGroups.remove(groupRef)
                }
            }
        }
    }

    /**
     * Remove an object from the table, using the reference string it knows
     * for itself.  It is permissible to remove an object that isn't actually
     * there.
     *
     * @param object  The object to be removed.
     */
    fun remove(`object`: Referenceable) {
        remove(`object`.ref())
    }

    init {
        addRef(ErrorHandler(baseCommGorgel.getChild(ErrorHandler::class)))
    }
}
