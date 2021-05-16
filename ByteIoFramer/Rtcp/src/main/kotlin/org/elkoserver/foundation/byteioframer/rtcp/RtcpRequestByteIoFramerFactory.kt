package org.elkoserver.foundation.byteioframer.rtcp

import com.grack.nanojson.JsonParserException
import org.elkoserver.foundation.byteioframer.ByteIoFramer
import org.elkoserver.foundation.byteioframer.ByteIoFramerFactory
import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStream
import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStreamFactory
import org.elkoserver.foundation.byteioframer.MessageReceiver
import org.elkoserver.foundation.byteioframer.readASCIILine
import org.elkoserver.foundation.byteioframer.readUTF8Line
import org.elkoserver.foundation.net.Communication
import org.elkoserver.json.JsonParsing.jsonObjectFromString
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Byte I/O framer factory for RTCP requests.  The framing rule used is: read
 * one line &amp; interpret it as an RTCP request line.  If the request is not a
 * message delivery, then framing is complete at this point.  If it *is* a
 * message delivery, then continue, following exactly the message framing rule
 * implemented by the `JsonByteIoFramerFactory` class: read a block of
 * one or more non-empty lines terminated by an empty line (i.e., by two
 * successive newlines).
 *
 * On recognition of an RTCP request as a message delivery, each block
 * matching the JSON framing rule is regarded as a parseable unit; that is, it
 * is expected to contain one or more syntactically complete JSON messages.
 * The entire block is read into an internal buffer, then parsed for JSON
 * messages that are fed to the receiver.
 *
 * On output, each thing being sent is always in the form of a string by the
 * time this class gets its hands on it, so output framing consists of merely
 * ensuring that the proper character encoding is used.
 */
class RtcpRequestByteIoFramerFactory(private val gorgel: Gorgel, private val chunkyByteArrayInputStreamFactory: ChunkyByteArrayInputStreamFactory, private val mustSendDebugReplies: Boolean) :
    ByteIoFramerFactory {

    /**
     * Provide an I/O framer for a new RTCP connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    override fun provideFramer(receiver: MessageReceiver, label: String): ByteIoFramer =
            RtcpRequestFramer(receiver, label, chunkyByteArrayInputStreamFactory.create())

    /**
     * I/O framer implementation for RTCP requests.
     */
    private inner class RtcpRequestFramer(
            private val myReceiver: MessageReceiver,
            private val myLabel: String,
            private val myIn: ChunkyByteArrayInputStream
    ) : ByteIoFramer {

        /** JSON message input currently in progress.  */
        private val myMsgBuffer: StringBuilder = StringBuilder(1000)

        /** Stage of RTCP request reading.  */
        private var myRTCPParseStage = RTCP_STAGE_REQUEST

        /** RTCP request object under construction.  */
        private var myRequest = RtcpRequest()

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
                when (myRTCPParseStage) {
                    RTCP_STAGE_REQUEST -> {
                        val line = myIn.readASCIILine()
                        if (line == null) {
                            myIn.preserveBuffers()
                            return
                        } else if (line.isNotEmpty()) {
                            gorgel.d?.run { debug("$myLabel |> $line") }
                            myRequest.parseRequestLine(line)
                            if (!myRequest.isComplete) {
                                myRTCPParseStage = RTCP_STAGE_MESSAGES
                            }
                        }
                    }
                    RTCP_STAGE_MESSAGES -> {
                        val line = myIn.readUTF8Line()
                        if (line == null) {
                            myIn.preserveBuffers()
                            return
                        }
                        when {
                            line.isEmpty() -> {
                                var needsFurtherParsing = true
                                while (needsFurtherParsing) {
                                    try {
                                        val obj = jsonObjectFromString(myMsgBuffer.toString())
                                        if (obj == null) {
                                            needsFurtherParsing = false
                                        } else {
                                            myRequest.addMessage(obj)
                                        }
                                    } catch (e: JsonParserException) {
                                        needsFurtherParsing = false
                                        if (mustSendDebugReplies) {
                                            myRequest.noteProblem(e)
                                        }
                                        gorgel.warn("syntax error in JSON message: ${e.message}")
                                    }
                                }
                                myMsgBuffer.setLength(0)
                            }
                            myMsgBuffer.length + line.length > Communication.MAX_MSG_LENGTH -> throw IOException("input too large (limit ${Communication.MAX_MSG_LENGTH} bytes)")
                            else -> {
                                myMsgBuffer.append(' ')
                                myMsgBuffer.append(line)
                            }
                        }
                    }
                }
                if (myRequest.isComplete) {
                    myReceiver.receiveMsg(myRequest)
                    myRequest = RtcpRequest()
                    myRTCPParseStage = RTCP_STAGE_REQUEST
                }
            }
        }

        /**
         * Generate the bytes for writing a message to a connection.
         *
         * @param message  The message to be written.  In this case, the
         * message must be a String.
         *
         * @return a byte array containing the writable form of 'message'.
         */
        @Throws(IOException::class)
        override fun produceBytes(message: Any): ByteArray {
            val reply: String
            if (message is String) {
                reply = message
                gorgel.d?.run { debug("to=$myLabel writeMessage=${reply.length}") }
            } else {
                throw IOException("unwritable message type: ${message.javaClass}")
            }
            gorgel.d?.run { debug("RTCP sending:\n$reply") }
            return reply.toByteArray(StandardCharsets.UTF_8)
        }
    }

    companion object {
        /** Stage is: parsing request line  */
        private const val RTCP_STAGE_REQUEST = 1

        /** Stage is: parsing JSON message block  */
        private const val RTCP_STAGE_MESSAGES = 2
    }
}
