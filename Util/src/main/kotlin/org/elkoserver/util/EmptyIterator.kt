package org.elkoserver.util

import java.util.NoSuchElementException

/**
 * Iterator for a collection of no elements.  This is sort of the Iterator
 * equivalent of a null pointer.
 */
class EmptyIterator<V>
/**
 * Constructor.
 */
private constructor() : MutableIterator<V> {
    /**
     * Returns true if the iteration has more elements.
     *
     * @return false (since, by definition, there are no elements).
     */
    override fun hasNext() = false

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration (actually, this will never
     * happen; it will always throw).
     *
     * @throws NoSuchElementException  iteration has no more elements (always).
     */
    override fun next(): V {
        throw NoSuchElementException()
    }

    /**
     * Removes from the underlying collection the last element returned by the
     * iterator; will always throw an exception since there never was such an
     * element.
     *
     * @throws IllegalStateException since there are no elements in this
     * collection.
     */
    override fun remove() {
        throw IllegalStateException()
    }

    companion object {
        private val INSTANCE: EmptyIterator<*> = EmptyIterator<Any>()

        @JvmStatic
        fun <TElement> emptyIterator(): EmptyIterator<TElement> {
            return INSTANCE as EmptyIterator<TElement>
        }
    }
}
