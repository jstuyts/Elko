package org.elkoserver.foundation.json

/**
 * An exception somewhere in the process of performing the reflection
 * operations involved in invoking a method or constructor from a JSON object.
 * This may occur, for example, as the result of receiving a malformed JSON
 * message.
 */
internal class JsonInvocationException : Exception {
    /**
     * Construct a JSONInvocationException with no specified detail message.
     */
    constructor() : super()

    /**
     * Construct a JSONInvocationException with the specified detail message.
     *
     * @param message  The detail message.
     */
    constructor(message: String?) : super(message)
}