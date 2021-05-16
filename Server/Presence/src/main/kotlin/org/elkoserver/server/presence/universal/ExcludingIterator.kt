package org.elkoserver.server.presence.universal

/**
 * Iterator over a collection that excludes a distinguished element.
 *
 * @param myBase  The underlying iterator.
 */
internal abstract class ExcludingIterator<V> protected constructor(private val myBase: Iterator<V>) : MutableIterator<V> {

    /** Single element lookahead, so we can tell if we're done.  */
    private var myLookahead: V? = null

    private fun skipExcludedElements() {
        myLookahead = null
        while (myBase.hasNext()) {
            val elem = myBase.next()
            if (!isExcluded(elem)) {
                myLookahead = elem
                break
            }
        }
    }

    /**
     * Test if a given element should be excluded from the iteration.
     * Sub-classes implement this.
     *
     * @param element  The element to be tested.
     *
     * @return true if the element should be excluded from the iteration,
     * false if if should be included.
     */
    protected abstract fun isExcluded(element: V): Boolean

    /**
     * Returns true if the iteration has more elements.  (In other words,
     * returns true if [next] would return an element rather than
     * throwing an exception.)
     *
     * @return true if the iterator has more elements.
     */
    override fun hasNext() = myLookahead != null

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration.
     *
     * @throws NoSuchElementException  iteration has no more elements.
     */
    override fun next() =
            if (hasNext()) {
                val result = myLookahead!!
                skipExcludedElements()
                result
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

    init {
        skipExcludedElements()
    }
}
