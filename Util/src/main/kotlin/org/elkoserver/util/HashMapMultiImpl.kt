package org.elkoserver.util

/**
 * A hashtable-like collection that maps each key to a set of items rather than
 * to a single item.
 */
class HashMapMultiImpl<K : Any, V : Any> : MutableHashMapMulti<K, V> {
    /** Maps keys --> sets of values  */
    private val myMap: MutableMap<K, MutableHashSetMulti<V>> = HashMap()

    /** Version number, for detecting modification during iteration  */
    private var myVersionNumber = 0

    /**
     * Add a value to a key's value set.
     *
     * @param key  The key for the value to add.
     * @param value  The value that should be added to 'key's value set
     */
    override fun add(key: K, value: V) {
        val set = myMap.computeIfAbsent(key, { HashSetMultiImpl() })
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
    override fun containsKey(key: K) = myMap.containsKey(key)

    /**
     * Return the set of values for some key.  Note that a set will always be
     * returned; if the given key has no values then the set returned will be
     * empty.
     *
     * @param key  The key for the set of values desired.
     *
     * @return a set of the values for 'key'.
     */
    override fun getMulti(key: K) = myMap[key] ?: HashSetMultiImpl()

    /**
     * Get the set of keys for this map.
     *
     * @return the keys for this map.
     */
    override fun keys(): Set<K> = myMap.keys

    /**
     * Remove a value from a key's value set.
     *
     * @param key  The key for the value set to remove from.
     * @param value The value that should be removed from 'key's value
     * set
     */
    override fun remove(key: K, value: V) {
        myMap[key]?.let {
            it.remove(value)
            if (it.isEmpty) {
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
    override fun remove(key: K) {
        myMap.remove(key)
        ++myVersionNumber
    }

    /**
     * Return an iterable that can iterate over all the values of all the keys
     * in this map.
     *
     * @return an iterable for the values in this map.
     */
    override fun values() = object : Iterable<V> {
        override fun iterator() = HashMapMultiValueIterator()
    }

    private inner class HashMapMultiValueIterator : Iterator<V> {
        private val mySetIter = myMap.values.iterator()
        private var myValueIter: Iterator<V>? = null
        private var myNext: V? = null
        private val myStartVersionNumber = myVersionNumber

        override fun hasNext(): Boolean {
            assertNotConcurrentlyModified()
            return myNext != null
        }

        override fun next(): V {
            assertNotConcurrentlyModified()
            val result = myNext!!
            advance()
            return result
        }

        private fun assertNotConcurrentlyModified() {
            if (myVersionNumber != myStartVersionNumber) {
                throw ConcurrentModificationException()
            }
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
            advance()
        }
    }
}
