package org.elkoserver.util

import org.elkoserver.util.EmptyIterator.Companion.emptyIterator
import java.util.HashMap

/**
 * A hash "set" that objects can be added to multiple times.  An object must be
 * removed an equal number of times before it disappears.
 *
 * This class is to [HashMapMulti] as [HashSet][java.util.HashSet]
 * is to [HashMap], but note that it does not truly implement the set
 * abstraction since the number of times a value is entered is significant.
 */
class HashSetMulti<V> : Iterable<V> {
    /** Maps objects --> counts of membership  */
    private val myMembers: MutableMap<V, Int>?

    /** Flag blocking modification, to implement a read-only view.  */
    private val amReadOnly: Boolean

    /**
     * Construct a new, empty set.
     */
    constructor() {
        myMembers = HashMap()
        amReadOnly = false
    }

    /**
     * Private constructor for creating read-only sets.
     */
    private constructor(map: MutableMap<V, Int>?) {
        myMembers = map
        amReadOnly = true
    }

    /**
     * Add an object to the set.
     *
     * @param obj  The object to add.
     */
    fun add(obj: V) {
        if (amReadOnly) {
            throw UnsupportedOperationException("read-only set")
        }
        var count = myMembers!![obj]
        count = if (count == null) {
            1
        } else {
            count + 1
        }
        myMembers[obj] = count
    }

    /**
     * Produce a new set that is a read-only version of this one.
     *
     * @return an unmodifiable view onto this set.
     */
    fun asUnmodifiable() = HashSetMulti(myMembers)

    /**
     * Test if a given object is a member of the set (i.e., that it has been
     * added more times than it has been removed).
     *
     * @param obj  The object to test for.
     *
     * @return true if 'obj' is a member of this set.
     */
    operator fun contains(obj: V) = myMembers?.containsKey(obj) ?: false

    /**
     * Test if this set is empty.
     *
     * @return true if this set contains no members.
     */
    val isEmpty: Boolean
        get() = myMembers == null || myMembers.isEmpty()

    /**
     * Obtain an iterator over the objects in this set (not repeating
     * multiples).
     *
     * @return an iterator over the objects contained by this set.  Note that
     * each object that is in the set is returned exactly once, regardless
     * of how many times it has been added.
     */
    override fun iterator() = myMembers?.keys?.iterator() ?: emptyIterator()

    /**
     * Remove an object from the set.  Note that the object only really
     * disappears from the set once it has been removed as many times as it
     * was added.  Removes in excess of adds are silently ignored.
     *
     * @param obj  The object to remove.
     */
    fun remove(obj: V) {
        if (amReadOnly) {
            throw UnsupportedOperationException("read-only set")
        }
        val count = myMembers!![obj]
        if (count != null) {
            val newCount = count - 1
            if (newCount == 0) {
                myMembers.remove(obj)
            } else {
                myMembers[obj] = newCount
            }
        }
    }

    companion object {
        /**
         * Produce an empty set.
         *
         * @return a read-only empty set.
         */
        fun <V> emptySet(): HashSetMulti<V> = HashSetMulti(null)
    }
}
