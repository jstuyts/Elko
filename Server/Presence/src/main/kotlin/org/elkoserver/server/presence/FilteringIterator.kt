package org.elkoserver.server.presence

/**
 * Iterator that takes an iterator producing values of one type and generates
 * values of another type via a transformation method that is supplied by the
 * implementing subclass.
 *
 * @param myBase  The iterator whose elements are to be transformed.
 * @param myFilter  The filter to transform the iterata.
 */
internal class FilteringIterator<From, To>(private val myBase: Iterator<From>, private val myFilter: Filter<From, To>) : MutableIterator<To> {

    /**
     * Returns true if the iteration has more elements.  (In other words,
     * returns true if [.next] would return an element rather than
     * throwing an exception.)
     *
     * @return true if the iterator has more elements.
     */
    override fun hasNext() = myBase.hasNext()

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration.
     *
     * @throws NoSuchElementException  iteration has no more elements.
     */
    override fun next() = myFilter.transform(myBase.next())

    /**
     * This operation is not supported.
     *
     * @throws UnsupportedOperationException always.
     */
    override fun remove() {
        throw UnsupportedOperationException()
    }

    /**
     * Utility class implemented by filters used by the [ ] iterator class.
     */
    internal interface Filter<From, To> {
        /**
         * Generate an object of type To given an object of type From.
         *
         * @param from  The object to be transformed.
         *
         * @return the object 'from' transforms into.
         */
        fun transform(from: From): To
    }
}
