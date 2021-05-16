package org.elkoserver.foundation.byteioframer.http

import org.elkoserver.foundation.byteioframer.*
import org.elkoserver.foundation.net.Communication
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Byte I/O framer factory for HTTP requests.  The framing rules implemented
 * by this class are the message formatting rules for HTTP requests and
 * responses as described by "RFC 2616: Hypertext Transfer Protocol --
 * HTTP/1.1", except that chunked transfer coding is not supported.
 */
class HttpRequestByteIoFramerFactory(private val baseCommGorgel: Gorgel, private val chunkyByteArrayInputStreamFactory: ChunkyByteArrayInputStreamFactory) : ByteIoFramerFactory {

    /**
     * Provide an I/O framer for a new HTTP connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    override fun provideFramer(receiver: MessageReceiver, label: String): ByteIoFramer =
            HttpRequestFramer(receiver, label, baseCommGorgel.getChild(HttpRequestFramer::class), chunkyByteArrayInputStreamFactory.create())

    /**
     * I/O framer implementation for HTTP requests.
     */
    private class HttpRequestFramer(private val myReceiver: MessageReceiver, private val myLabel: String, private val commGorgel: Gorgel, private val myIn: ChunkyByteArrayInputStream) : ByteIoFramer {

        /** Stage of HTTP request reading.  */
        private var myHTTPParseStage = HTTP_STAGE_START

        /** HTTP request object under construction.  */
        private var myRequest = HttpRequest()

        /**
         * Process bytes of data received.
         *
         * @param data   The bytes received.
         * @param length  Number of usable bytes in 'data'.  End of input is
         * indicated by passing a 'length' value of 0.
         */
        @Throws(IOException::class)
        override fun receiveBytes(data: ByteArray, length: Int) {
            myIn.addBuffer(data, length)
            while (true) {
                when (myHTTPParseStage) {
                    HTTP_STAGE_START -> {
                        val line = myIn.readASCIILine()
                        if (line == null) {
                            myIn.preserveBuffers()
                            return
                        } else if (line.isNotEmpty()) {
                            myRequest.parseStartLine(line)
                            myHTTPParseStage = HTTP_STAGE_HEADER
                        }
                    }
                    HTTP_STAGE_HEADER -> {
                        val line = myIn.readASCIILine()
                        when {
                            line == null -> {
                                myIn.preserveBuffers()
                                return
                            }
                            line.isEmpty() -> myHTTPParseStage = HTTP_STAGE_BODY
                            else -> myRequest.parseHeaderLine(line)
                        }
                    }
                    HTTP_STAGE_BODY -> {
                        val bodyLen = myRequest.contentLength
                        if (bodyLen > Communication.MAX_MSG_LENGTH) {
                            throw IOException("message too large: $bodyLen > ${Communication.MAX_MSG_LENGTH}")
                        } else if (bodyLen > 0) {
                            if (myIn.available() < bodyLen) {
                                myIn.preserveBuffers()
                                return
                            }
                            myIn.updateUsefulByteCount(bodyLen)
                            val contentBuilder = StringBuilder(bodyLen)
                            val isr = InputStreamReader(myIn, StandardCharsets.UTF_8)
                            var character = isr.read()
                            while (character != -1) {
                                contentBuilder.append(character.toChar())
                                character = isr.read()
                            }
                            myRequest.content = contentBuilder.toString()
                        }
                        myReceiver.receiveMsg(myRequest)
                        myRequest = HttpRequest()
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
        override fun produceBytes(message: Any): ByteArray {
            var reply: String
            when (message) {
                is String -> {
                    reply = message
                    commGorgel.d?.run { debug("to=$myLabel writeMessage=${reply.length}") }
                    reply = """
                        HTTP/1.1 200 OK
                        Cache-Control: no-cache
                        Access-Control-Allow-Origin: *
                        Content-Type: text/plain; charset=UTF-8
                        Content-Length: ${utf8Length(reply)}
                        
                        $reply
                        """.trimIndent()
                }
                is HttpError -> {
                    reply = message.messageString
                    reply = """HTTP/1.1 ${message.errorNumber} ${message.errorString}
    Access-Control-Allow-Origin: *
    Content-Length: ${utf8Length(reply)}
    
    $reply"""
                }
                is HttpOptionsReply -> reply = """
                    HTTP/1.1 200 OK
                    Access-Control-Allow-Origin: *
                    Access-Control-Max-Age: 31536000
                    Access-Control-Allow-Methods: GET, POST, OPTIONS
                    ${message.headersHeader()}Content-Type: text/plain
                    Content-Length: 0
                    
                    
                    """.trimIndent()
                else -> throw IOException("unwritable message type: ${message.javaClass}")
            }
            commGorgel.d?.run { debug("HTTP sending:\n$reply") }
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
