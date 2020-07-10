package org.elkoserver.foundation.byteioframer.http

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.HashMap

/**
 * An HTTP request descriptor, obtained by parsing the lines of text in an HTTP
 * request as they are received.
 */
open class HttpRequest {
    /** The method from the HTTP start line.  */
    var method: String? = null
        private set

    /** The URI from the HTTP start line.  */
    var uri: String? = null
        private set

    /** All the headers, indexed by name.  */
    private val myHeaders: MutableMap<String, String> = HashMap()

    /** Value of the Content-Length header.  */
    var contentLength: Int = 0
        private set

    /**
     * Test if this is a non-persistent connection.
     *
     * @return true if a header line said "Connection: close".
     */
    /** Flag whether Connection header says "close".  */
    var isNonPersistent: Boolean = false
        private set

    /** Flag whether Content-Type header says URL encoding is in use.  */
    private var amURLEncoded = false

    /** The message body, if there is one.  */
    var content: String? = null
        internal set(value) {
            field = if (amURLEncoded) {
                URLDecoder.decode(value, StandardCharsets.UTF_8)
            } else {
                value
            }
        }

    /**
     * Get the value of a request header field.
     *
     * @param name  The header name whose value is desired.
     *
     * @return the value of the header named by 'name', or null if there is no
     * such header in the request.
     */
    fun header(name: String): String? = myHeaders[name]

    /**
     * Parse a header line, adding the header it contains to the header table.
     *
     * @param line  The line to be parsed.
     */
    fun parseHeaderLine(line: String) {
        var actualLine = line
        actualLine = actualLine.trim { it <= ' ' }
        val colon = actualLine.indexOf(':')
        if (0 < colon && colon < actualLine.length - 1) {
            val name = actualLine.take(colon).trim { it <= ' ' }.toLowerCase()
            val value = actualLine.substring(colon + 1).trim { it <= ' ' }
            myHeaders[name] = value
            when (name) {
                "content-length" -> contentLength = value.toInt()
                "connection" -> isNonPersistent = value.equals("close", ignoreCase = true)
                "content-type" -> amURLEncoded = value.equals("application/x-www-form-urlencoded", ignoreCase = true)
            }
        }
    }

    /**
     * Parse an HTTP start line, extracting the method name and URI.
     *
     * @param line  The line to be parsed.
     */
    fun parseStartLine(line: String) {
        var actualLine = line
        actualLine = actualLine.trim { it <= ' ' }
        var methodEnd = actualLine.indexOf(' ')
        if (methodEnd >= 0) {
            method = actualLine.take(methodEnd)
            ++methodEnd
            val uriEnd = actualLine.indexOf(' ', methodEnd)
            if (uriEnd >= 0) {
                uri = actualLine.substring(methodEnd, uriEnd).toLowerCase()
            }
        }
    }

    /**
     * Obtain a printable String representation of this request.
     *
     * @return a printable dump of the request state.
     */
    override fun toString(): String {
        val result = StringBuilder("HTTP Request $method for $uri\n")
        for ((key, value) in myHeaders) {
            result.append(key).append(": ").append(value).append("\n")
        }
        if (content == null) {
            result.append("Content: <none>\n")
        } else {
            result.append("Content: /").append(content).append("/\n")
        }
        return result.toString()
    }
}
