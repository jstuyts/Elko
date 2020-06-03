package org.elkoserver.server.presence

import java.util.NoSuchElementException

/**
 * Iterator over an array of objects.  Usable in iterator-based for loops when
 * the underlying array is null, and when you actually need an explicit
 * iterator.
 *
 * @param myArray  The array to iterate over.
 */
internal class ArrayIterator<out V>(private val myArray: Array<V>) : MutableIterator<V> {

    /** Iteration position  */
    private var myIndex = 0

    /**
     * Returns true if the iteration has more elements.  (In other words,
     * returns true if [.next] would return an element rather than
     * throwing an exception.)
     *
     * @return true if the iterator has more elements.
     */
    override fun hasNext() = myIndex < myArray.size

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration.
     *
     * @throws NoSuchElementException  iteration has no more elements.
     */
    override fun next() =
            if (hasNext()) {
                myArray[myIndex++]
            } else {
                throw NoSuchElementException()
            }

    /**
     * This operation is not supported.
     *
     * @throws UnsupportedOperationException always.
     */
    override fun remove() {
        throw UnsupportedOperationException()
    }
}
