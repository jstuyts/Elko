package org.elkoserver.util

/**
 * A hash "set" that objects can be added to multiple times.  An object must be
 * removed an equal number of times before it disappears.
 *
 * This class is to [HashMapMultiImpl] as [HashSet][HashSet]
 * is to [HashMap], but note that it does not truly implement the set
 * abstraction since the number of times a value is entered is significant.
 */
class HashSetMultiImpl<V : Any> : MutableHashSetMulti<V> {
    /** Maps objects --> counts of membership  */
    private val myMembers = mutableMapOf<V, Int>()

    /**
     * Add an object to the set.
     *
     * @param obj  The object to add.
     */
    override fun add(obj: V) {
        myMembers.merge(obj, 1) { current, _ -> current + 1 }
    }

    /**
     * Test if a given object is a member of the set (i.e., that it has been
     * added more times than it has been removed).
     *
     * @param obj  The object to test for.
     *
     * @return true if 'obj' is a member of this set.
     */
    override operator fun contains(obj: V) = myMembers.containsKey(obj)

    /**
     * Test if this set is empty.
     *
     * @return true if this set contains no members.
     */
    override val isEmpty
        get() = myMembers.isEmpty()

    /**
     * Obtain an iterator over the objects in this set (not repeating
     * multiples).
     *
     * @return an iterator over the objects contained by this set.  Note that
     * each object that is in the set is returned exactly once, regardless
     * of how many times it has been added.
     */
    override fun iterator() = myMembers.keys.iterator()

    /**
     * Remove an object from the set.  Note that the object only really
     * disappears from the set once it has been removed as many times as it
     * was added.  Removes in excess of adds are silently ignored.
     *
     * @param obj  The object to remove.
     */
    override fun remove(obj: V) {
        myMembers.compute(obj) { _, current ->
            when (current) {
                null -> null
                1 -> null
                else -> current - 1
            }
        }
    }
}
