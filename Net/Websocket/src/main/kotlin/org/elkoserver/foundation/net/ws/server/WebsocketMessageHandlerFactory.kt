package org.elkoserver.foundation.net.ws.server

import org.elkoserver.foundation.byteioframer.http.HttpError
import org.elkoserver.foundation.byteioframer.websocket.WebsocketHandshake
import org.elkoserver.foundation.byteioframer.websocket.WebsocketRequest
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Arrays
import java.util.stream.Collectors

/**
 * Message handler factory to provide message handlers that wrap a message
 * stream inside a WebSocket connection.
 *
 * Each HTTP message handler wraps an application-level message handler,
 * which is the entity that will actually process the messages extracted
 * from the HTTP requests, so the HTTP message handler factory needs to
 * wrap the application-level message handler factory.
 *
 * @param myInnerFactory  The application-level message handler factor that
 * is to be wrapped by this.
 * @param mySocketUri  The URI of the WebSocket connection point.
 */
internal class WebsocketMessageHandlerFactory(
        private val myInnerFactory: MessageHandlerFactory,
        private val mySocketUri: String,
        private val gorgel: Gorgel) : MessageHandlerFactory {

    private fun makeErrorReply(problem: String): String {
        return """
            <!DOCTYPE HTML PUBLIC "-//IETF//DTD HTML 2.0//EN">
            <html><head>
            <title>400 Bad Request</title>
            </head><body>
            <h1>Bad Request</h1>
            <p>WebSocket connection setup failed: $problem.</p>
            </body></html>
            
            
            """.trimIndent()
    }

    /**
     * Transmit an HTTP error reply for a bad WS connection request.
     *
     * @param connection  The connection upon which the bad request was
     * received.
     * @param problem  The error that is being reported
     */
    private fun sendError(connection: Connection, problem: String) {
        gorgel.i?.run { info("$connection received invalid WebSocket connection startup: $problem") }
        connection.sendMsg(HttpError(400, "Bad Request",
                makeErrorReply(problem)))
    }

    private fun doConnectionHandshake(connection: Connection, request: WebsocketRequest) {
        val key = request.header("sec-websocket-key")
        val key1 = request.header("sec-websocket-key1")
        val key2 = request.header("sec-websocket-key2")
        if (!request.method.equals("GET", ignoreCase = true)) {
            sendError(connection, "WebSocket connection start requires GET")
        } else if (!request.uri.equals(mySocketUri, ignoreCase = true)) {
            sendError(connection, "Invalid WebSocket endpoint URI")
        } else if (!"WebSocket".equals(request.header("upgrade"), ignoreCase = true)) {
            sendError(connection, "Invalid WebSocket Upgrade header")
        } else if (!getConnectionValues(request).contains("Upgrade")) {
            sendError(connection, "Invalid WebSocket Connection header")
        } else if (key != null) {
            connection.sendMsg(generateRidiculousHandshake6(key))
        } else if (request.crazyKey == null) {
            sendError(connection, "Invalid WebSocket client token")
        } else if (key1 == null || key2 == null) {
            sendError(connection, "Invalid WebSocket key header")
        } else {
            connection.sendMsg(generateRidiculousHandshake0(key1, key2,
                    request.crazyKey))
        }
    }

    private fun getConnectionValues(request: WebsocketRequest): Set<String> = Arrays.stream(request.header("connection")!!.split(",").toTypedArray()).map { obj: String -> obj.trim { it <= ' ' } }.collect(Collectors.toSet())

    private fun insaneKeyDecode(key: String): Long {
        var spaceCount = 0
        var num: Long = 0
        val len = key.length
        for (i in 0 until len) {
            val c = key[i]
            if (c in '0'..'9') {
                num = num * 10 + c.toLong() - '0'.toLong()
            } else if (c == ' ') {
                ++spaceCount
            }
        }
        return num / spaceCount
    }

    private fun generateRidiculousHandshake0(key1: String,
                                             key2: String,
                                             crazyKey: ByteArray?): WebsocketHandshake {
        return try {
            val md5 = MessageDigest.getInstance("MD5")
            val numBytes = ByteArray(4)
            var keyNum = insaneKeyDecode(key1)
            numBytes[0] = (keyNum shr 24).toByte()
            numBytes[1] = (keyNum shr 16).toByte()
            numBytes[2] = (keyNum shr 8).toByte()
            numBytes[3] = keyNum.toByte()
            md5.update(numBytes)
            keyNum = insaneKeyDecode(key2)
            numBytes[0] = (keyNum shr 24).toByte()
            numBytes[1] = (keyNum shr 16).toByte()
            numBytes[2] = (keyNum shr 8).toByte()
            numBytes[3] = keyNum.toByte()
            md5.update(numBytes)
            gorgel.d?.run { debug("Crazy key = ${String.format("%02x %02x %02x %02x %02x %02x %02x %02x", crazyKey!![0], crazyKey[1], crazyKey[2], crazyKey[3], crazyKey[4], crazyKey[5], crazyKey[6], crazyKey[7])}") }
            WebsocketHandshake(0, md5.digest(crazyKey))
        } catch (e: NoSuchAlgorithmException) {
            throw UnsupportedOperationException("MD5 not available", e)
        }
    }

    private fun generateRidiculousHandshake6(key: String): WebsocketHandshake {
        return try {
            val sha1 = MessageDigest.getInstance("SHA-1")
            val inString = key + MAGIC_WS_HANDSHAKE_GUID
            sha1.update(inString.toByteArray(StandardCharsets.ISO_8859_1))
            WebsocketHandshake(6, sha1.digest())
        } catch (e: NoSuchAlgorithmException) {
            throw UnsupportedOperationException("SHA1 not available", e)
        }
    }

    /**
     * Provide a message handler for a new WebSocket connection.
     *
     * @param connection  The TCP connection object that was just created.
     */
    override fun provideMessageHandler(connection: Connection?): MessageHandler = WebsocketMessageHandler(myInnerFactory.provideMessageHandler(connection)!!)

    private inner class WebsocketMessageHandler(var myInnerHandler: MessageHandler) : MessageHandler {

        /**
         * Cope with connection death.
         *
         * @param connection  The connection that has just died.
         * @param reason  A possible indication why the connection went away.
         */
        override fun connectionDied(connection: Connection, reason: Throwable) {
            myInnerHandler.connectionDied(connection, reason)
        }

        /**
         * Process an incoming message from a connection.
         *
         * @param connection  The connection upon which the message arrived.
         * @param message  The incoming message.
         */
        override fun processMessage(connection: Connection, message: Any) {
            if (message is WebsocketRequest) {
                doConnectionHandshake(connection, message)
            } else {
                myInnerHandler.processMessage(connection, message)
            }
        }

    }

    companion object {
        private const val MAGIC_WS_HANDSHAKE_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }
}
