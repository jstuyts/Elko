package org.elkoserver.foundation.net

import com.grack.nanojson.JsonParserException
import org.elkoserver.json.JsonParsing.jsonObjectFromString
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Byte I/O framer factory for RTCP requests.  The framing rule used is: read
 * one line &amp; interpret it as an RTCP request line.  If the request is not a
 * message delivery, then framing is complete at this point.  If it *is* a
 * message delivery, then continue, following exactly the message framing rule
 * implemented by the `JSONByteIOFramerFactory` class: read a block of
 * one or more non-empty lines terminated by an empty line (i.e., by two
 * successive newlines).
 *
 *
 * On recognition of an RTCP request as a message delivery, each block
 * matching the JSON framing rule is regarded as a parseable unit; that is, it
 * is expected to contain one or more syntactically complete JSON messages.
 * The entire block is read into an internal buffer, then parsed for JSON
 * messages that are fed to the receiver.
 *
 *
 * On output, each thing being sent is always in the form of a string by the
 * time this class gets its hands on it, so output framing consists of merely
 * ensuring that the proper character encoding is used.
 */
class RTCPRequestByteIOFramerFactory(private val trMsg: Trace, private val inputGorgel: Gorgel, private val mustSendDebugReplies: Boolean) : ByteIOFramerFactory {

    /**
     * Provide an I/O framer for a new RTCP connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    override fun provideFramer(receiver: MessageReceiver, label: String): ByteIOFramer =
            RTCPRequestFramer(receiver, label)

    /**
     * I/O framer implementation for RTCP requests.
     */
    private inner class RTCPRequestFramer internal constructor(
            private val myReceiver: MessageReceiver,
            private val myLabel: String) : ByteIOFramer {

        /** Input data source.  */
        private val myIn: ChunkyByteArrayInputStream = ChunkyByteArrayInputStream(inputGorgel)

        /** JSON message input currently in progress.  */
        private val myMsgBuffer: StringBuilder = StringBuilder(1000)

        /** Stage of RTCP request reading.  */
        private var myRTCPParseStage = Companion.RTCP_STAGE_REQUEST

        /** RTCP request object under construction.  */
        private var myRequest = RTCPRequest()

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
                    Companion.RTCP_STAGE_REQUEST -> {
                        val line = myIn.readASCIILine()
                        if (line == null) {
                            myIn.preserveBuffers()
                            return
                        } else if (line.isNotEmpty()) {
                            if (trMsg.debug) {
                                trMsg.debugm("$myLabel |> $line")
                            }
                            myRequest.parseRequestLine(line)
                            if (!myRequest.isComplete) {
                                myRTCPParseStage = Companion.RTCP_STAGE_MESSAGES
                            }
                        }
                    }
                    Companion.RTCP_STAGE_MESSAGES -> {
                        val line = myIn.readUTF8Line()
                        if (line == null) {
                            myIn.preserveBuffers()
                            return
                        }
                        if (line.isEmpty()) {
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
                                    if (trMsg.warning) {
                                        trMsg.warningm("syntax error in JSON message: ${e.message}")
                                    }
                                }
                            }
                            myMsgBuffer.setLength(0)
                        } else if (myMsgBuffer.length + line.length >
                                Communication.MAX_MSG_LENGTH) {
                            throw IOException("input too large (limit ${Communication.MAX_MSG_LENGTH} bytes)")
                        } else {
                            myMsgBuffer.append(' ')
                            myMsgBuffer.append(line)
                        }
                    }
                }
                if (myRequest.isComplete) {
                    myReceiver.receiveMsg(myRequest)
                    myRequest = RTCPRequest()
                    myRTCPParseStage = Companion.RTCP_STAGE_REQUEST
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
                if (trMsg.verbose) {
                    trMsg.verbosem("to=$myLabel writeMessage=${reply.length}")
                }
            } else {
                throw IOException("unwritable message type: ${message.javaClass}")
            }
            if (trMsg.debug) {
                trMsg.debugm("RTCP sending:\n$reply")
            }
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
