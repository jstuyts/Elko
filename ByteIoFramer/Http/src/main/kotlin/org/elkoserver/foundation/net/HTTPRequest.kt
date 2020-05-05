package org.elkoserver.foundation.net

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.HashMap

/**
 * An HTTP request descriptor, obtained by parsing the lines of text in an HTTP
 * request as they are received.
 */
open class HTTPRequest {
    /** The method from the HTTP start line.  */
    private var myMethod: String? = null

    /** The URI from the HTTP start line.  */
    private var myURI: String? = null

    /** All the headers, indexed by name.  */
    private val myHeaders: MutableMap<String, String> = HashMap()

    /** Value of the Content-Length header.  */
    private var myContentLength = 0

    /**
     * Test if this is a non-persistent connection.
     *
     * @return true if a header line said "Connection: close".
     */
    /** Flag whether Connection header says "close".  */
    var isNonPersistent = false
        private set

    /** Flag whether Content-Type header says URL encoding is in use.  */
    private var amURLEncoded = false

    /** The message body, if there is one.  */
    private var myContent: String? = null

    /**
     * Get the message body content.
     *
     * @return the request message body content as a string, or null if there
     * is none.
     */
    fun content() = myContent

    /**
     * Get the message body length.
     *
     * @return the length of the message body, or 0 if there is no body.
     */
    fun contentLength() = myContentLength

    /**
     * Get the value of a request header field.
     *
     * @param name  The header name whose value is desired.
     *
     * @return the value of the header named by 'name', or null if there is no
     * such header in the request.
     */
    fun header(name: String) = myHeaders[name]

    /**
     * Get the request method (GET, PUT, etc.).
     *
     * @return the request method.
     */
    fun method() = myMethod

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
            val name = actualLine.substring(0, colon).trim { it <= ' ' }.toLowerCase()
            val value = actualLine.substring(colon + 1).trim { it <= ' ' }
            myHeaders[name] = value
            when (name) {
                "content-length" -> myContentLength = value.toInt()
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
            myMethod = actualLine.substring(0, methodEnd)
            ++methodEnd
            val uriEnd = actualLine.indexOf(' ', methodEnd)
            if (uriEnd >= 0) {
                myURI = actualLine.substring(methodEnd, uriEnd).toLowerCase()
            }
        }
    }

    /**
     * Record the request's message body content.
     *
     * @param content  The body itself.
     */
    fun setContent(content: String?) {
        myContent = if (amURLEncoded) {
            URLDecoder.decode(content, StandardCharsets.UTF_8)
        } else {
            content
        }
    }

    /**
     * Obtain a printable String representation of this request.
     *
     * @return a printable dump of the request state.
     */
    override fun toString(): String {
        val result = StringBuilder("HTTP Request $myMethod for $myURI\n")
        for ((key, value) in myHeaders) {
            result.append(key).append(": ").append(value).append("\n")
        }
        if (myContent == null) {
            result.append("Content: <none>\n")
        } else {
            result.append("Content: /").append(myContent).append("/\n")
        }
        return result.toString()
    }

    /**
     * Get the URI that was requested.
     *
     * @return the requested URI.
     */
    fun URI() = myURI
}
