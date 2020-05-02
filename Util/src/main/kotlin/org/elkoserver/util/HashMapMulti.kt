package org.elkoserver.util

import java.util.ConcurrentModificationException
import java.util.HashMap

/**
 * A hashtable-like collection that maps each key to a set of items rather than
 * to a single item.
 */
class HashMapMulti<K, V> {
    /** Maps keys --> sets of values  */
    private val myMap: MutableMap<K, HashSetMulti<V>> = HashMap()

    /** Version number, for detecting modification during iteration  */
    private var myVersionNumber = 0

    /**
     * Add a value to a key's value set.
     *
     * @param key  The key for the value to add.
     * @param value  The value that should be added to 'key's value set
     */
    fun add(key: K, value: V) {
        var set = myMap[key]
        if (set == null) {
            set = HashSetMulti()
            myMap[key] = set
        }
        set.add(value)
        ++myVersionNumber
    }

    /**
     * Test if this map has a mapping for a given key.
     *
     * @param key  The key whose potential mapping is of interest.
     *
     * @return true if this map has one or more values for key, false if not.
     */
    fun containsKey(key: K): Boolean {
        return myMap.containsKey(key)
    }

    /**
     * Return the set of values for some key.  Note that a set will always be
     * returned; if the given key has no values then the set returned will be
     * empty.
     *
     * @param key  The key for the set of values desired.
     *
     * @return a set of the values for 'key'.
     */
    fun getMulti(key: K): HashSetMulti<V> {
        var result = myMap[key]
        if (result == null) {
            result = HashSetMulti.emptySet()
        }
        return result
    }

    /**
     * Get the set of keys for this map.
     *
     * @return the keys for this map.
     */
    fun keys(): Set<K> {
        return myMap.keys
    }

    /**
     * Remove a value from a key's value set.
     *
     * @param key  The key for the value set to remove from.
     * @param value The value that should be removed from 'key's value
     * set
     */
    fun remove(key: K, value: V) {
        val set = myMap[key]
        if (set != null) {
            set.remove(value)
            if (set.isEmpty) {
                myMap.remove(key)
            }
        }
        ++myVersionNumber
    }

    /**
     * Remove a key's entire value set.
     *
     * @param key  The key for the set of values to remove.
     */
    fun remove(key: K) {
        myMap.remove(key)
        ++myVersionNumber
    }

    /**
     * Return an iterable that can iterate over all the values of all the keys
     * in this map.
     *
     * @return an iterable for the values in this map.
     */
    fun values(): Iterable<V> = object : Iterable<V> {
        override fun iterator() = HashMapMultiValueIterator()
    }

    private inner class HashMapMultiValueIterator internal constructor() : MutableIterator<V> {
        private val mySetIter: Iterator<HashSetMulti<V>>
        private var myValueIter: Iterator<V>? = null
        private var myNext: V? = null
        private val myStartVersionNumber: Int = myVersionNumber
        override fun hasNext(): Boolean {
            if (myVersionNumber != myStartVersionNumber) {
                throw ConcurrentModificationException()
            }
            return myNext != null
        }

        override fun next(): V {
            if (myVersionNumber != myStartVersionNumber) {
                throw ConcurrentModificationException()
            }
            val result = myNext!!
            advance()
            return result
        }

        override fun remove() {
            throw UnsupportedOperationException()
        }

        private fun advance() {
            while (myValueIter == null || !myValueIter!!.hasNext()) {
                if (mySetIter.hasNext()) {
                    myValueIter = mySetIter.next().iterator()
                } else {
                    myNext = null
                    return
                }
            }
            myNext = myValueIter!!.next()
        }

        init {
            mySetIter = myMap.values.iterator()
            advance()
        }
    }
}
