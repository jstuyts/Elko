package org.elkoserver.foundation.net

/**
 * Helper class to hold onto the various fragments of a parsed HTTP session
 * URI.
 *
 * @param uri  The URI string to parse.
 * @param rootUri  The root URI that should begin all URIs.
 *
 * The URI string must be one of the following forms:
 *
 * ROOTURI/connect
 * ROOTURI/connect/RANDOMCRUDTHATISIGNORED
 * ROOTURI/xmit/SESSIONID/SEQUENCENUMBER
 * ROOTURI/select/SESSIONID/SEQUENCENUMBER
 * ROOTURI/disconnect/SESSIONID
 *
 * where ROOTURI is the string passed in the 'rootURI' parameter, and
 * SESSIONID and SEQUENCENUMBER are decimal integers.
 */
class SessionUri(uri: String, rootUri: String) {
    /** Verb from the URI, encoded as one of the constants VERB_xxx  */
    val verb: Int

    /** Session ID from the URI.  */
    val sessionID: Long

    /** Message sequence number from the URI.  */
    val sequenceNumber: Int

    /** Set to true if parsing was successful, false if the URI was bad.  */
    val valid: Boolean

    /**
     * Extract the next numeric component from a URI string (up to the next
     * '/' character or the end of string).
     *
     * @param stringptr  Length 1 array holding URI string.
     *
     * @return the integer value of the component at the head of stringptr[0].
     *
     * On exit, if parsing was successfull, stringptr[0] will point to the tail
     * of the URI string following the integer component that was extracted
     * (and following the trailing delimiter, if there was one).  If parsing
     * failed, stringptr[0] will be null.
     */
    private fun intComponent(stringptr: Array<String?>): Long {
        var str = stringptr[0]
        val slash = str!!.indexOf('/')
        if (slash < 0) {
            stringptr[0] = ""
        } else {
            stringptr[0] = str.substring(slash + 1)
            str = str.take(slash)
        }
        return try {
            str.toLong()
        } catch (e: NumberFormatException) {
            stringptr[0] = null
            -1
        }
    }

    companion object {
        /* Path name elements for the four verbs. */
        private const val CONNECT_REQ_URI = "connect"
        private const val SELECT_REQ_URI = "select/"
        private const val XMIT_REQ_URI = "xmit/"
        private const val DISCONNECT_REQ_URI = "disconnect/"

        /* Constants identifying verbs. */
        const val VERB_CONNECT: Int = 1
        const val VERB_SELECT: Int = 2
        const val VERB_XMIT_GET: Int = 3
        const val VERB_XMIT_POST: Int = 4
        const val VERB_DISCONNECT: Int = 5
    }

    init {
        var actualUri = uri
        var initSessionID: Long = 0
        var initVerb = 0
        var initSequenceNumber = 0
        var initValid = false
        if (actualUri.startsWith(rootUri)) {
            actualUri = actualUri.substring(rootUri.length)
            if (actualUri.endsWith("/")) {
                actualUri = actualUri.dropLast(1)
            }
            if (actualUri.startsWith(CONNECT_REQ_URI)) {
                if (actualUri.length == CONNECT_REQ_URI.length) {
                    actualUri = ""
                    initVerb = VERB_CONNECT
                    initValid = true
                } else if (actualUri[CONNECT_REQ_URI.length] == '/') {
                    actualUri = actualUri.substring(CONNECT_REQ_URI.length + 1)
                    initVerb = VERB_CONNECT
                    initValid = true
                }
            } else if (actualUri.startsWith(SELECT_REQ_URI)) {
                actualUri = actualUri.substring(SELECT_REQ_URI.length)
                initVerb = VERB_SELECT
                initValid = true
            } else if (actualUri.startsWith(XMIT_REQ_URI)) {
                actualUri = actualUri.substring(XMIT_REQ_URI.length)
                initVerb = VERB_XMIT_POST
                initValid = true
            } else if (actualUri.startsWith(DISCONNECT_REQ_URI)) {
                actualUri = actualUri.substring(DISCONNECT_REQ_URI.length)
                initVerb = VERB_DISCONNECT
                initValid = true
            }
        }
        if (initValid) {
            initValid = false
            if (initVerb == VERB_CONNECT) {
                initValid = true
            } else {
                val stringptr: Array<String?> = arrayOf(actualUri)
                initSessionID = intComponent(stringptr)
                if (stringptr[0] != null) {
                    if (initVerb == VERB_DISCONNECT) {
                        initValid = true
                    } else {
                        initSequenceNumber = intComponent(stringptr).toInt()
                        if (stringptr[0] != null) {
                            initValid = true
                        }
                    }
                }
            }
        }
        sessionID = initSessionID
        sequenceNumber = initSequenceNumber
        verb = initVerb
        valid = initValid
    }
}
