package org.elkoserver.foundation.json

/**
 * An error somewhere in the process of performing the reflection operations to
 * prepare to invoke methods or constructors from a JSON object.  This error
 * will happen only if the [JsonMethod] annotations on a JSON driven
 * method or constructor are incorrectly specified; this should never happen
 * during normal operation as a result of message receipt.
 */
internal class JsonSetupError : Error {
    /**
     * Construct a JSONSetupError with no specified detail message.
     */
    constructor() : super()

    /**
     * Construct a JSONSetupError with the specified detail message.
     *
     * @param message  The detail message.
     */
    constructor(message: String?) : super(message)
}