package org.elkoserver.foundation.net

import org.elkoserver.json.JsonObject
import java.util.LinkedList
import java.util.regex.Pattern

/**
 * An RTCP request descriptor, obtained by parsing the lines of text in an RTCP
 * request as they are received.
 */
class RTCPRequest {
    /** State of request parsing  */
    private var myParseState = STATE_AWAITING_VERB

    /** RTCP request verb  */
    private var myVerb = 0
    /* The following elements will be present or not according to whether the
       message indicated by the verb is supposed to container them. */
    /** Highest seq number of message from us that client claims receipt of.  */
    private var myClientRecvSeqNum = 0

    /** Seq number of message bundle from client to us (message delivery).  */
    private var myClientSendSeqNum = 0

    /** Session ID ("resume").  */
    private var mySessionID: String? = null

    /** Error tag ("error").  */
    private var myError: String? = null

    /** First message in a message bundle (message delivery).  */
    private var myMessage: JsonObject? = null

    /** Second and later messages in a message bundle, or null if there was
     * only one (message delivery).  */
    private var myOtherMessages: LinkedList<JsonObject>? = null

    /**
     * Add a message to the message bundle this descriptor holds.  This is for
     * use by the external entity responsible for parsing the remainder of a
     * message delivery after this class has parsed the request line.
     *
     * @param message  The message to be added.
     */
    fun addMessage(message: JsonObject) {
        myParseState = STATE_COMPLETE
        if (myMessage == null) {
            myMessage = message
        } else {
            val currentOtherMessages = myOtherMessages
            val actualOtherMessages = if (currentOtherMessages == null) {
                val newOtherMessages = LinkedList<JsonObject>()
                myOtherMessages = newOtherMessages
                newOtherMessages
            } else {
                currentOtherMessages
            }
            actualOtherMessages.addLast(message)
        }
    }

    /**
     * Obtain the client received sequence number, which is the highest message
     * sequence number of messages from us to the client that client claims to
     * have received successfully.
     *
     * This will be present in "resume", "ack", "end", and message delivery
     * requests.
     *
     * @return this request's client receive sequence number.
     */
    fun clientRecvSeqNum() = myClientRecvSeqNum

    /**
     * Obtain the client send sequence number, which is the sequence number of
     * message bundle carried by this message delivery request.
     *
     * @return this request's client send sequence number.
     */
    fun clientSendSeqNum() = myClientSendSeqNum

    /**
     * Obtain the error tag from this request.  This will be present in "error"
     * requests and as the consequence of parse failures.
     *
     * @return this request's error tag string.
     */
    fun error() = myError

    /**
     * Test if the request described by this object has been completely parsed
     * yet.  If not, the various request parameter values will not be valid.
     *
     * @return true iff request parsing is complete.
     */
    val isComplete: Boolean
        get() = myParseState == STATE_COMPLETE

    /**
     * Obtain the next message from the message bundle in this request.  This
     * method will provide one message each time it is called until the
     * messages in the request have been exhausted, after which it will always
     * return null.  Messages, of course, will only be present in message
     * delivery requests.
     *
     * @return the next available message in the this request, or null if all
     * messages have been previously returned.
     */
    fun nextMessage(): JsonObject? {
        val result = myMessage
        val currentOtherMessages = myOtherMessages
        if (currentOtherMessages != null) {
            myMessage = currentOtherMessages.removeFirst()
            if (currentOtherMessages.isEmpty()) {
                myOtherMessages = null
            }
        } else {
            myMessage = null
        }
        return result
    }

    /**
     * Take note of an external parsing or I/O problem that prevents successful
     * completion of a valid RTCP request.
     *
     * @param problem  Exception describing what the issue was
     */
    fun noteProblem(problem: Exception) {
        myError = problem.message
        myParseState = STATE_COMPLETE
        myVerb = VERB_ERROR
    }

    /**
     * Parse an RTCP request line, extracting the verb, and parameters if any.
     *
     * Note that this only parses the request line.  In the case of a message
     * delivery, the portion of the request containing the message bundle
     * itself is processed externally.
     *
     * @param line  The line to be parsed.
     */
    fun parseRequestLine(line: String) {
        var actualLine = line
        actualLine = actualLine.trim { it <= ' ' }
        val frags = theDelimiterPattern.split(actualLine)
        val verb = frags[0]
        myParseState = STATE_COMPLETE
        if (verb == "start") {
            myVerb = VERB_START
            if (frags.size != 1) {
                myVerb = VERB_ERROR
                myError = "invalid start request"
            }
        } else if (verb == "resume") {
            myVerb = VERB_RESUME
            if (frags.size != 3) {
                myVerb = VERB_ERROR
                myError = "invalid resume request"
            } else {
                mySessionID = frags[1]
                try {
                    myClientRecvSeqNum = frags[2].toInt()
                } catch (e: NumberFormatException) {
                    myVerb = VERB_ERROR
                    myError = "invalid resume request"
                }
            }
        } else if (verb == "ack") {
            myVerb = VERB_ACK
            if (frags.size != 2) {
                myVerb = VERB_ERROR
                myError = "invalid resume request"
            } else {
                try {
                    myClientRecvSeqNum = frags[1].toInt()
                } catch (e: NumberFormatException) {
                    myVerb = VERB_ERROR
                    myError = "invalid ack request"
                }
            }
        } else if (verb == "end") {
            myVerb = VERB_END
            if (frags.size != 2) {
                myVerb = VERB_ERROR
                myError = "invalid end request"
            } else {
                try {
                    myClientRecvSeqNum = frags[1].toInt()
                } catch (e: NumberFormatException) {
                    myVerb = VERB_ERROR
                    myError = "invalid end request"
                }
            }
        } else if (verb == "error") {
            myVerb = VERB_ERROR
            myError = if (frags.size != 2) {
                "invalid error request"
            } else {
                "client reported error: ${frags[1]}"
            }
        } else {
            myVerb = VERB_MESSAGE
            try {
                myClientSendSeqNum = verb.toInt()
                if (frags.size != 2) {
                    myVerb = VERB_ERROR
                    myError = "invalid message request"
                } else {
                    try {
                        myClientRecvSeqNum = frags[1].toInt()
                    } catch (e: NumberFormatException) {
                        myVerb = VERB_ERROR
                        myError = "invalid message request"
                    }
                }
            } catch (e: NumberFormatException) {
                myVerb = VERB_ERROR
                myError = "invalid RTCP verb $verb"
            }
            if (myVerb == VERB_MESSAGE) {
                myParseState = STATE_AWAITING_MESSAGE
            }
        }
    }

    /**
     * Obtain the session ID from this request.  This will be present only in
     * "resume" requests.
     *
     * @return this request's session ID.
     */
    fun sessionID() = mySessionID

    /**
     * Obtain this request's verb.  This will be one of the VERB_XXX
     * constants defined by this class.
     *
     * @return This requests' request verb code.
     */
    fun verb() = myVerb

    /**
     * Obtain a printable String representation of this request.
     *
     * @return a printable dump of the request state.
     */
    override fun toString(): String {
        return when (myVerb) {
            VERB_START -> "start"
            VERB_RESUME -> "resume $mySessionID $myClientRecvSeqNum"
            VERB_ACK -> "ack $myClientRecvSeqNum"
            VERB_MESSAGE -> "msg $myClientSendSeqNum $myClientRecvSeqNum"
            VERB_END -> "end $myClientRecvSeqNum"
            VERB_ERROR -> "error $myError"
            else -> "<unknown RTCP verb>"
        }
    }

    companion object {
        private const val STATE_AWAITING_VERB = 0
        private const val STATE_AWAITING_MESSAGE = 1
        private const val STATE_COMPLETE = 2
        const val VERB_START = 0
        const val VERB_RESUME = 1
        const val VERB_ACK = 2
        const val VERB_MESSAGE = 3
        const val VERB_END = 4
        const val VERB_ERROR = 6

        /** Regexp to match one or more spaces.  Compiled and cached here.  */
        private val theDelimiterPattern = Pattern.compile(" +")
    }
}
