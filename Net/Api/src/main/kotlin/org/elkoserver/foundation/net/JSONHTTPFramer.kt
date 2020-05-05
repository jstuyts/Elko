package org.elkoserver.foundation.net

import com.grack.nanojson.JsonParserException
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JsonObject
import org.elkoserver.json.JsonObjectSerialization.sendableString
import org.elkoserver.json.JsonParsing.jsonObjectFromString
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * HTTP message framer for JSON messages transported via HTTP.
 *
 * This class treats the content of each HTTP POST to the /xmit/ URL as a
 * bundle of one or more JSON messages to be handled.
 *
 * FIXME: nanomsg parser cannot parse multiple messages in 1 string
 */
class JSONHTTPFramer (appTrace: Trace, private val traceFactory: TraceFactory) : HTTPFramer(appTrace) {

    /**
     * Produce the HTTP for responding to an HTTP GET of the /select/ URL by
     * sending a message to the client.
     *
     *
     * The actual HTTP reply body sent is constructed by concatenating the
     * results of one or more coordinated calls to this method, one call for
     * each message that is to be sent.  In the first of these calls, the
     * 'start' flag must be true.  In the last, the 'end' flag must be true.
     * (If only one message is being sent, this method should be called exactly
     * once with both 'start' and 'end' set to true.)
     *
     * @param message  The message to be sent.
     * @param seqNumber  The sequence number for the next select request.
     * @param start  true if this message is the first in a batch of messages.
     * @param end  true if this message is the last in a batch of messages.
     *
     * @return an appropriate HTTP reply body string for responding to a
     * /select/ GET, delivering 'message' to the client.
     */
    override fun makeSelectReplySegment(message: Any?, seqNumber: Int, start: Boolean, end: Boolean): String {
        val messageString = if (message is JSONLiteral) {
            message.sendableString()
        } else if (message is JsonObject) {
            sendableString(message)
        } else if (message is String) {
            "\"$message\""
        } else {
            null
        }
        return super.makeSelectReplySegment(messageString, seqNumber,
                start, end)
    }

    /**
     * Get an iterator that can extract the JSON message or messages (if any)
     * from the body of an HTTP message.
     *
     * @param body  The HTTP message body in question.
     *
     * @return an iterator that can be called upon to return the JSON
     * message(s) contained within 'body'.
     */
    override fun postBodyUnpacker(body: String): Iterator<Any> {
        return JSONBodyUnpacker(body, traceFactory)
    }

    /**
     * Post body unpacker for a bundle of JSON messages.  In this case, the
     * HTTP POST body contains one or more JSON messages.
     */
    private class JSONBodyUnpacker internal constructor(postBody: String, private val traceFactory: TraceFactory) : MutableIterator<Any> {
        private var postBody: String

        /** Last JSON message parsed.  This will be the next JSON message to be
         * returned because the framer always parses one JSON message ahead,
         * in order to be able to see the end of the HTTP message string.  */
        private var myLastMessageParsed: Any?

        // XXX Temporary hack to decode POSTs from Safari
        private fun extractBodyFromSafariPostIfNeeded(postBody: String): String {
            var actualPostBody = postBody
            val junkMark = actualPostBody.indexOf('=')
            if (junkMark >= 0) {
                if (actualPostBody.length > junkMark &&
                        actualPostBody[junkMark + 1] == '%') {
                    actualPostBody = URLDecoder.decode(actualPostBody, StandardCharsets.UTF_8)
                }
                val startOfMessageMark = actualPostBody.indexOf('{')
                if (startOfMessageMark > junkMark) {
                    actualPostBody = actualPostBody.substring(junkMark + 1)
                }
            }
            return actualPostBody
        }

        /**
         * Test if there are more messages.
         *
         * Since the framer always parse one message ahead, just look to see if
         * there is a message sitting there.
         */
        override fun hasNext(): Boolean {
            return myLastMessageParsed != null
        }

        /**
         * Get the next message.
         *
         * Return the last message parsed, then parse the next message for the
         * next time this is called (the parser will return null after it has
         * parsed the last actual message).
         */
        override fun next(): Any {
            val result = myLastMessageParsed!!
            myLastMessageParsed = parseNextMessage()
            return result
        }

        /**
         * Helper routine to actually do the message parsing.
         */
        private fun parseNextMessage(): Any? {
            return try {
                val result = jsonObjectFromString(postBody)
                postBody = ""
                result
            } catch (e: JsonParserException) {
                if (Communication.TheDebugReplyFlag) {
                    return e
                }
                if (traceFactory.comm.warning) {
                    traceFactory.comm.warningm("syntax error in JSON message: ${e.message}")
                }
                null
            }
        }

        override fun remove() {
            throw UnsupportedOperationException()
        }

        /**
         * Constructor. Strip the form variable name and parse the rest as
         * JSON.
         *
         * @param postBody  The HTTP message body was POSTed.
         */
        init {
            traceFactory.comm.debugm("unpacker for: /$postBody/")
            this.postBody = extractBodyFromSafariPostIfNeeded(postBody)
            myLastMessageParsed = parseNextMessage()
        }
    }

}