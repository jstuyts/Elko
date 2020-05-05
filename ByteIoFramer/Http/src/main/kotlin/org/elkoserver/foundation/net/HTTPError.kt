package org.elkoserver.foundation.net

/**
 * Class encapsulating an HTTP error that is being output as the reply to
 * an HTTP request.
 *
 * @param myErrorNumber  The HTTP error number (e.g., 404 for not found).
 * @param myErrorString  The error string (e.g., "Not Found").
 * @param myMessageString  The message body HTML.
 */
class HTTPError(private val myErrorNumber: Int, private val myErrorString: String, private val myMessageString: String) {

    /**
     * Obtain the error number.
     *
     * @return the error number.
     */
    fun errorNumber() = myErrorNumber

    /**
     * Obtain the error string.
     *
     * @return the error string.
     */
    fun errorString() = myErrorString

    /**
     * Obtain the message string.
     *
     * @return the message string.
     */
    fun messageString() = myMessageString
}
