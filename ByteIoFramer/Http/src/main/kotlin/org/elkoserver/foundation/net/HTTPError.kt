package org.elkoserver.foundation.net

/**
 * Class encapsulating an HTTP error that is being output as the reply to
 * an HTTP request.
 *
 * @param errorNumber  The HTTP error number (e.g., 404 for not found).
 * @param errorString  The error string (e.g., "Not Found").
 * @param messageString  The message body HTML.
 */
class HTTPError(val errorNumber: Int, val errorString: String, val messageString: String)
