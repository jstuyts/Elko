package org.elkoserver.foundation.net

import org.elkoserver.json.JSONLiteral
import org.elkoserver.util.ByteArrayToAscii.byteArrayToASCII
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Byte I/O framer factory for WebSocket connections, a perverse hybrid of HTTP
 * and TCP.
 *
 * @param trMsg  Trace object for logging message traffic.
 */
class WebSocketByteIOFramerFactory(private val trMsg: Trace, private val myHostAddress: String, private val mySocketURI: String, private val traceFactory: TraceFactory) : ByteIOFramerFactory {

    /** The host address, stripped of port number.  */
    private var myHostName: String? = null

    /**
     * Provide an I/O framer for a new HTTP connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    override fun provideFramer(receiver: MessageReceiver, label: String): ByteIOFramer = WebSocketFramer(receiver, label, traceFactory)

    /**
     * I/O framer implementation for HTTP requests.
     */
    private inner class WebSocketFramer internal constructor(
            private val myReceiver: MessageReceiver,
            private val myLabel: String, traceFactory: TraceFactory) : ByteIOFramer {

        /** Input data source.  */
        private val myIn: ChunkyByteArrayInputStream = ChunkyByteArrayInputStream(traceFactory)

        /** Lower-level framer once we start actually reading messages.  */
        private var myMessageFramer: JSONByteIOFramer? = null

        /** Stage of WebSocket input reading.  */
        private var myWSParseStage: Int

        /** HTTP request object under construction, for start handshake.  */
        private val myRequest: WebSocketRequest

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
                when (myWSParseStage) {
                    Companion.WS_STAGE_START -> {
                        val line = myIn.readASCIILine()
                        if (line == null) {
                            myIn.preserveBuffers()
                            return
                        } else if (line.isNotEmpty()) {
                            myRequest.parseStartLine(line)
                            myWSParseStage = Companion.WS_STAGE_HEADER
                        }
                    }
                    Companion.WS_STAGE_HEADER -> {
                        val line = myIn.readASCIILine()
                        if (line == null) {
                            myIn.preserveBuffers()
                            return
                        } else if (line.isEmpty()) {
                            myWSParseStage = Companion.WS_STAGE_HANDSHAKE
                        } else {
                            myRequest.parseHeaderLine(line)
                        }
                    }
                    Companion.WS_STAGE_HANDSHAKE -> {
                        if (myRequest.header("sec-websocket-key1") != null) {
                            val crazyKey = myIn.readBytes(8)
                            if (crazyKey == null) {
                                myIn.preserveBuffers()
                                return
                            }
                            myRequest.crazyKey = crazyKey
                        }
                        myReceiver.receiveMsg(myRequest)
                        myWSParseStage = Companion.WS_STAGE_MESSAGES
                        myIn.enableWebSocketFraming()
                        myMessageFramer = JSONByteIOFramer(trMsg, myReceiver, myLabel, myIn)
                        return
                    }
                    Companion.WS_STAGE_MESSAGES -> {
                        myMessageFramer!!.receiveBytes(ByteArray(0), 0)
                        return
                    }
                }
            }
        }

        /**
         * Generate the bytes for writing a message to a connection.  In this
         * case, a message must be a string, a WebSocketHandshake object, or an
         * HTTPError object.  A string is considered to be a serialized JSON
         * message; it should be transmitted inside a WebSocket message
         * frame. A WebSocketHandshake object contains the information for a
         * connection setup handshake; it should be transmitted as the
         * appropriate HTTP header plus junk. An HTTPError object is just what
         * it seems to be; it should be transmitted as a regular HTTP error
         * response.
         *
         * @param message  The message to be written.
         *
         * @return a byte array containing the writable form of 'msg'.
         */
        @Throws(IOException::class)
        override fun produceBytes(message: Any): ByteArray {
            var msg = message
            if (msg is JSONLiteral) {
                msg = msg.sendableString()
            }
            return if (msg is String) {
                val msgString = msg
                if (trMsg.event) {
                    trMsg.msgi(myLabel, false, msgString)
                }
                val msgBytes = msgString.toByteArray(StandardCharsets.UTF_8)
                val frame = ByteArray(msgBytes.size + 2)
                frame[0] = 0x00
                System.arraycopy(msgBytes, 0, frame, 1, msgBytes.size)
                frame[frame.size - 1] = 0xFF.toByte()
                if (trMsg.debug) {
                    trMsg.debugm("WS sending msg: $msg")
                }
                frame
            } else if (msg is WebSocketHandshake) {
                val handshake = msg
                if (handshake.version == 0) {
                    val handshakeBytes = handshake.bytes
                    val header = """
                    HTTP/1.1 101 WebSocket Protocol Handshake
                    Upgrade: WebSocket
                    Connection: Upgrade
                    Sec-WebSocket-Origin: http://$myHostName
                    Sec-WebSocket-Location: ws://$myHostAddress$mySocketURI
                    Sec-WebSocket-Protocol: *
                    
                    
                    """.trimIndent()
                    val headerBytes = header.toByteArray(StandardCharsets.US_ASCII)
                    val reply = ByteArray(headerBytes.size + handshakeBytes.size)
                    System.arraycopy(headerBytes, 0, reply, 0,
                            headerBytes.size)
                    System.arraycopy(handshakeBytes, 0, reply,
                            headerBytes.size, handshakeBytes.size)
                    if (trMsg.debug) {
                        trMsg.debugm("WS sending handshake:\n$header${byteArrayToASCII(handshakeBytes, 0, handshakeBytes.size)}")
                    }
                    reply
                } else if (handshake.version == 6) {
                    val header = """
                    HTTP/1.1 101 Switching Protocols
                    Upgrade: Websocket
                    Connection: Upgrade
                    Sec-WebSocket-Accept: ${base64Encoder.encodeToString(handshake.bytes)}
                    
                    
                    """.trimIndent()
                    val headerBytes = header.toByteArray(StandardCharsets.US_ASCII)
                    if (trMsg.debug) {
                        trMsg.debugm("WS sending handshake:\n$header")
                    }
                    headerBytes
                } else {
                    throw Error("unsupported WebSocket version")
                }
            } else if (msg is HTTPError) {
                val error = msg
                var reply = error.messageString
                reply = """HTTP/1.1 ${error.errorNumber} ${error.errorString}
Access-Control-Allow-Origin: *
Content-Length: ${reply.length}

$reply"""
                if (trMsg.debug) {
                    trMsg.debugm("WS sending error:\n$reply")
                }
                reply.toByteArray(StandardCharsets.US_ASCII)
            } else {
                throw IOException("unwritable message type: ${msg.javaClass}")
            }
        }

        /**
         * Constructor.
         */
        init {
            myWSParseStage = Companion.WS_STAGE_START
            myRequest = WebSocketRequest()
        }
    }

    companion object {
        /** Stage is: parsing method line  */
        private const val WS_STAGE_START = 1

        /** Stage is: parsing headers  */
        private const val WS_STAGE_HEADER = 2

        /** Stage is: parsing handshake bytes  */
        private const val WS_STAGE_HANDSHAKE = 3

        /** Stage is: parsing message stream  */
        private const val WS_STAGE_MESSAGES = 4

        private val base64Encoder = Base64.getEncoder()
    }

    init {
        val colonPos = myHostAddress.indexOf(':')
        myHostName = if (colonPos != -1) {
            myHostAddress.take(colonPos)
        } else {
            myHostAddress
        }
    }
}
