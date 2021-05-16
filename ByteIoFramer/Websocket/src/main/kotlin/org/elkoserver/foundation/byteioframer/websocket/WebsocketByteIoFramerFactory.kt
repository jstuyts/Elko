package org.elkoserver.foundation.byteioframer.websocket

import org.elkoserver.foundation.byteioframer.ByteIoFramer
import org.elkoserver.foundation.byteioframer.ByteIoFramerFactory
import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStream
import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStreamFactory
import org.elkoserver.foundation.byteioframer.MessageReceiver
import org.elkoserver.foundation.byteioframer.http.HttpError
import org.elkoserver.foundation.byteioframer.json.JsonByteIoFramer
import org.elkoserver.foundation.byteioframer.json.JsonByteIoFramerFactory
import org.elkoserver.foundation.byteioframer.readASCIILine
import org.elkoserver.json.JsonLiteral
import org.elkoserver.util.ByteArrayToAscii.byteArrayToASCII
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Byte I/O framer factory for WebSocket connections, a perverse hybrid of HTTP
 * and TCP.
 */
class WebsocketByteIoFramerFactory(
    private val websocketFramerGorgel: Gorgel,
    private val myHostAddress: String,
    private val mySocketUri: String,
    private val chunkyByteArrayInputStreamFactory: ChunkyByteArrayInputStreamFactory,
    private val jsonByteIoFramerFactory: JsonByteIoFramerFactory) : ByteIoFramerFactory {

    /** The host address, stripped of port number.  */
    private val myHostName: String

    /**
     * Provide an I/O framer for a new HTTP connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    override fun provideFramer(receiver: MessageReceiver, label: String): WebsocketFramer =
            WebsocketFramer(receiver, label, chunkyByteArrayInputStreamFactory.create())

    /**
     * I/O framer implementation for HTTP requests.
     */
    inner class WebsocketFramer internal constructor(
            private val myReceiver: MessageReceiver,
            private val myLabel: String,
            private val myIn: ChunkyByteArrayInputStream
    ) : ByteIoFramer {

        /** Lower-level framer once we start actually reading messages.  */
        private var myMessageFramer: JsonByteIoFramer? = null

        /** Stage of WebSocket input reading.  */
        private var myWSParseStage = WS_STAGE_START

        /** HTTP request object under construction, for start handshake.  */
        private val myRequest = WebsocketRequest()

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
                    WS_STAGE_START -> {
                        val line = myIn.readASCIILine()
                        if (line == null) {
                            myIn.preserveBuffers()
                            return
                        } else if (line.isNotEmpty()) {
                            myRequest.parseStartLine(line)
                            myWSParseStage = WS_STAGE_HEADER
                        }
                    }
                    WS_STAGE_HEADER -> {
                        val line = myIn.readASCIILine()
                        when {
                            line == null -> {
                                myIn.preserveBuffers()
                                return
                            }
                            line.isEmpty() -> myWSParseStage = WS_STAGE_HANDSHAKE
                            else -> myRequest.parseHeaderLine(line)
                        }
                    }
                    WS_STAGE_HANDSHAKE -> {
                        if (myRequest.header("sec-websocket-key1") != null) {
                            val crazyKey = myIn.readBytes(8)
                            if (crazyKey == null) {
                                myIn.preserveBuffers()
                                return
                            }
                            myRequest.crazyKey = crazyKey
                        }
                        myReceiver.receiveMsg(myRequest)
                        myWSParseStage = WS_STAGE_MESSAGES
                        myIn.enableWebsocketFraming()
                        myMessageFramer = jsonByteIoFramerFactory.provideFramer(myReceiver, myLabel, myIn)
                        return
                    }
                    WS_STAGE_MESSAGES -> {
                        myMessageFramer!!.receiveBytes(ByteArray(0), 0)
                        return
                    }
                }
            }
        }

        /**
         * Generate the bytes for writing a message to a connection.  In this
         * case, a message must be a string, a WebsocketHandshake object, or an
         * HTTPError object.  A string is considered to be a serialized JSON
         * message; it should be transmitted inside a WebSocket message
         * frame. A WebsocketHandshake object contains the information for a
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
            if (msg is JsonLiteral) {
                msg = msg.sendableString()
            }
            return when (msg) {
                is String -> {
                    val msgString = msg
                    websocketFramerGorgel.i?.run { info("$myLabel <- $msgString") }
                    val msgBytes = msgString.toByteArray(StandardCharsets.UTF_8)
                    val frame = ByteArray(msgBytes.size + 2)
                    frame[0] = 0x00
                    System.arraycopy(msgBytes, 0, frame, 1, msgBytes.size)
                    frame[frame.size - 1] = 0xFF.toByte()
                    websocketFramerGorgel.d?.run { debug("WS sending msg: $msg") }
                    frame
                }
                is WebsocketHandshake -> {
                    val handshake = msg
                    when (handshake.version) {
                        0 -> {
                            val handshakeBytes = handshake.bytes
                            val header = """
                                HTTP/1.1 101 WebSocket Protocol Handshake
                                Upgrade: WebSocket
                                Connection: Upgrade
                                Sec-WebSocket-Origin: http://$myHostName
                                Sec-WebSocket-Location: ws://$myHostAddress$mySocketUri
                                Sec-WebSocket-Protocol: *
                                
                                
                                """.trimIndent()
                            val headerBytes = header.toByteArray(StandardCharsets.US_ASCII)
                            val reply = ByteArray(headerBytes.size + handshakeBytes.size)
                            System.arraycopy(headerBytes, 0, reply, 0,
                                    headerBytes.size)
                            System.arraycopy(handshakeBytes, 0, reply,
                                    headerBytes.size, handshakeBytes.size)
                            websocketFramerGorgel.d?.run { debug("WS sending handshake:\n$header${byteArrayToASCII(handshakeBytes, 0, handshakeBytes.size)}") }
                            reply
                        }
                        6 -> {
                            val header = """
                                HTTP/1.1 101 Switching Protocols
                                Upgrade: Websocket
                                Connection: Upgrade
                                Sec-WebSocket-Accept: ${base64Encoder.encodeToString(handshake.bytes)}
                                
                                
                                """.trimIndent()
                            val headerBytes = header.toByteArray(StandardCharsets.US_ASCII)
                            websocketFramerGorgel.d?.run { debug("WS sending handshake:\n$header") }
                            headerBytes
                        }
                        else -> throw Error("unsupported WebSocket version")
                    }
                }
                is HttpError -> {
                    val error = msg
                    var reply = error.messageString
                    reply = """HTTP/1.1 ${error.errorNumber} ${error.errorString}
    Access-Control-Allow-Origin: *
    Content-Length: ${reply.length}
    
    $reply"""
                    websocketFramerGorgel.d?.run { debug("WS sending error:\n$reply") }
                    reply.toByteArray(StandardCharsets.US_ASCII)
                }
                else -> throw IOException("unwritable message type: ${msg.javaClass}")
            }
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
        myHostName = if (colonPos != -1) myHostAddress.take(colonPos) else myHostAddress
    }
}
