package org.elkoserver.json

/**
 * Thrown when a there is a problem of some sort interpreting the contents of a
 * JSON object.
 */
class JSONDecodingException : Exception {
    constructor()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}
