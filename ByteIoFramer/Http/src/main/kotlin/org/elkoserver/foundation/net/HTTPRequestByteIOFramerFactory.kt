package org.elkoserver.foundation.net

import org.elkoserver.util.trace.TraceFactory
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Byte I/O framer factory for HTTP requests.  The framing rules implemented
 * by this class are the message formatting rules for HTTP requests and
 * responses as described by "RFC 2616: Hypertext Transfer Protocol --
 * HTTP/1.1", except that chunked transfer coding is not supported.
 */
class HTTPRequestByteIOFramerFactory internal constructor(private val traceFactory: TraceFactory) : ByteIOFramerFactory {

    /**
     * Provide an I/O framer for a new HTTP connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    override fun provideFramer(receiver: MessageReceiver, label: String): ByteIOFramer =
            HTTPRequestFramer(receiver, label, traceFactory)

    /**
     * I/O framer implementation for HTTP requests.
     */
    private class HTTPRequestFramer internal constructor(private val myReceiver: MessageReceiver, private val myLabel: String, private val traceFactory: TraceFactory) : ByteIOFramer {

        /** Input data source.  */
        private val myIn = ChunkyByteArrayInputStream(traceFactory)

        /** Stage of HTTP request reading.  */
        private var myHTTPParseStage = HTTP_STAGE_START

        /** HTTP request object under construction.  */
        private var myRequest = HTTPRequest()

        /**
         * Process bytes of data received.
         *
         * @param data   The bytes received.
         * @param length  Number of usable bytes in 'data'.  End of input is
         * indicated by passing a 'length' value of 0.
         */
        @Throws(IOException::class)
        override fun receiveBytes(data: ByteArray?, length: Int) {
            myIn.addBuffer(data!!, length)
            while (true) {
                when (myHTTPParseStage) {
                    HTTP_STAGE_START -> {
                        val line = myIn.readASCIILine()
                        if (line == null) {
                            myIn.preserveBuffers()
                            return
                        } else if (line.length != 0) {
                            myRequest.parseStartLine(line)
                            myHTTPParseStage = HTTP_STAGE_HEADER
                        }
                    }
                    HTTP_STAGE_HEADER -> {
                        val line = myIn.readASCIILine()
                        if (line == null) {
                            myIn.preserveBuffers()
                            return
                        } else if (line.length == 0) {
                            myHTTPParseStage = HTTP_STAGE_BODY
                        } else {
                            myRequest.parseHeaderLine(line)
                        }
                    }
                    HTTP_STAGE_BODY -> {
                        val bodyLen = myRequest.contentLength()
                        if (bodyLen > Communication.MAX_MSG_LENGTH) {
                            throw IOException("message too large: " +
                                    bodyLen + " > " +
                                    Communication.MAX_MSG_LENGTH)
                        } else if (bodyLen > 0) {
                            if (myIn.available() < bodyLen) {
                                myIn.preserveBuffers()
                                return
                            } else {
                                myIn.updateUsefulByteCount(bodyLen)
                                val contentBuilder = StringBuilder(bodyLen)
                                val isr = InputStreamReader(myIn, StandardCharsets.UTF_8)
                                var character = isr.read()
                                while (character != -1) {
                                    contentBuilder.append(character.toChar())
                                    character = isr.read()
                                }
                                myRequest.setContent(contentBuilder.toString())
                            }
                        }
                        myReceiver.receiveMsg(myRequest)
                        myRequest = HTTPRequest()
                        myHTTPParseStage = HTTP_STAGE_START
                    }
                }
            }
        }

        /**
         * Determine the number of bytes in the UTF-8 encoding of a string.
         *
         * @param str  The string whose encoding length is of interest.
         *
         * @return the number of bytes it would take to encode the string in
         * UTF-8.
         */
        private fun utf8Length(str: String) =
                str.toByteArray(StandardCharsets.UTF_8).size // FIXME: VERY inefficient, OK for testing

        /**
         * Generate the bytes for writing a message to a connection.
         *
         * @param message  The message to be written.  In this case, the
         * message must be a String or an HTTPError.
         *
         * @return a byte array containing the writable form of 'message'.
         */
        @Throws(IOException::class)
        override fun produceBytes(message: Any?): ByteArray? {
            var reply: String
            if (message is String) {
                reply = message
                if (traceFactory.comm.verbose) {
                    traceFactory.comm.verbosem("to=" + myLabel +
                            " writeMessage=" + reply.length)
                }
                reply = """
                    HTTP/1.1 200 OK
                    Cache-Control: no-cache
                    Access-Control-Allow-Origin: *
                    Content-Type: text/plain; charset=UTF-8
                    Content-Length: ${utf8Length(reply)}
                    
                    $reply
                    """.trimIndent()
            } else if (message is HTTPError) {
                val error = message
                reply = error.messageString()
                reply = """HTTP/1.1 ${error.errorNumber()} ${error.errorString()}
Access-Control-Allow-Origin: *
Content-Length: ${utf8Length(reply)}

$reply"""
            } else if (message is HTTPOptionsReply) {
                reply = """
                    HTTP/1.1 200 OK
                    Access-Control-Allow-Origin: *
                    Access-Control-Max-Age: 31536000
                    Access-Control-Allow-Methods: GET, POST, OPTIONS
                    ${message.headersHeader()}Content-Type: text/plain
                    Content-Length: 0
                    
                    
                    """.trimIndent()
            } else {
                throw IOException("unwritable message type: " +
                        message!!.javaClass)
            }
            if (traceFactory.comm.debug) {
                traceFactory.comm.debugm("HTTP sending:\n$reply")
            }
            return reply.toByteArray(StandardCharsets.UTF_8)
        }

        companion object {
            /** Stage is: parsing method line  */
            private const val HTTP_STAGE_START = 1

            /** Stage is: parsing headers  */
            private const val HTTP_STAGE_HEADER = 2

            /** Stage is: parsing body  */
            private const val HTTP_STAGE_BODY = 3
        }

    }
}
