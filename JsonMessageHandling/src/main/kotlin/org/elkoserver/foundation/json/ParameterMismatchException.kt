package org.elkoserver.foundation.json

/**
 * An exception when the parameters of an invoked JSON method or JSON-driven
 * constructor don't match what was expected.
 */
internal class ParameterMismatchException : RuntimeException {
    /**
     * Constructor with no specified detail message.
     */
    constructor() : super() {}

    /**
     * Constructor.  Generates a detail message given descriptions of what
     * parameters were supplied vs. what were expected.
     *
     * @param suppliedParams  The supplied parameter objects.
     * @param expectedParams  The classes of the expected parameters.
     */
    constructor(suppliedParams: Array<Any?>, expectedParams: Array<Class<*>>) : super(createMessageString(suppliedParams, expectedParams)) {}

    companion object {
        /**
         * Generates a detail message for an exception, the parameters that were
         * supplied and the classes that what were expected.
         *
         * @param suppliedParams  The supplied parameter objects.
         * @param expectedParams  The classes of the expected parameters.
         */
        private fun createMessageString(suppliedParams: Array<Any?>,
                                        expectedParams: Array<Class<*>>): String {
            val count = suppliedParams.size.coerceAtMost(expectedParams.size)
            val message = StringBuilder()
            for (i in 0 until count) {
                if (!expectedParams[i].isAssignableFrom(suppliedParams[i]!!.javaClass)) {
                    message.append("Parameter mismatch: Method  requires ").append(expectedParams[i]).append("; found ").append(suppliedParams[i]!!.javaClass).append(" ")
                }
            }
            return message.toString()
        }
    }
}
