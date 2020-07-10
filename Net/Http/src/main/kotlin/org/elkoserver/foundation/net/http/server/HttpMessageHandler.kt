package org.elkoserver.foundation.net.http.server

import org.elkoserver.foundation.byteioframer.http.HttpRequest
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.Locale

/**
 * Message handler for HTTP requests wrapping a message stream.
 *
 * @param myConnection  The connection this is to be a message handler for.
 * @param myFactory  The factory what created this.
 * @param startupTimeoutInterval  How long to give new connection to do
 * something before kicking them off.
 */
class HttpMessageHandler(
        private val myConnection: Connection,
        private val myFactory: HttpMessageHandlerFactory,
        startupTimeoutInterval: Int,
        timer: Timer,
        private val commGorgel: Gorgel) : MessageHandler {

    /** Timeout for kicking off users who connect and then don't do anything  */
    private var myStartupTimeout: Timeout? = timer.after(
            startupTimeoutInterval.toLong(),
            object : TimeoutNoticer {
                override fun noticeTimeout() {
                    handleStartupTimeout()
                }
            })

    /** Flag that startup timeout has tripped, to detect late messages.  */
    private var myStartupTimeoutTripped = false

    /**
     * Receive notification that the connection has died.
     *
     * In this case, the connection is a TCP connection supporting HTTP, so it
     * doesn't really matter that it died.  Gratuitous TCP connection drops are
     * actually considered normal in the HTTP world.
     *
     * @param connection The (HTTP over TCP) connection that died.
     * @param reason  Why it died.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        myFactory.tcpConnectionDied(connection, reason)
    }

    /**
     * Handle an incoming message from the connection.
     *
     * Since this is an HTTP connection, the message (as parsed by the
     * HTTPRequestFramer) will be an HTTP request.  The nature of the request
     * determines what it is from the perspective of the higher level message
     * stream being supported.
     *
     * A GET for {ROOT}/connect initiates a new application-level session.
     * This should create a new application-level Connection object, plus
     * generate a session ID and key for the new session (which are sent
     * back to the client in the HTTP reply).
     *
     * A GET for {ROOT}/select/{SESSIONID}/{SEQNUM} is a poll for messages from
     * the server to the client.  {SESSIONID} identifies the session message
     * stream being polled and also verifies that the requestor is the true
     * client of the session.  {SEQNUM} ensures that the URI is unique, to
     * defeat caching by feckless intermediaries.
     *
     * A POST to {ROOT}/xmit/{SESSIONID}/{SEQNUM} is a delivery of messages
     * from the client to the server.  The interpretation of the URI
     * components is the same as in the /select/ URI.
     *
     * @param connection  The connection the message was received on.
     * @param message   The message that was received.  This must be an
     * instance of HTTPRequest.
     */
    override fun processMessage(connection: Connection, message: Any) {
        if (myStartupTimeoutTripped) {
            /* They were kicked off for lacktivity, so ignore the message. */
            return
        }

        myStartupTimeout?.cancel()
        myStartupTimeout = null

        val actualMessage = message as HttpRequest
        commGorgel.d?.run {
            debug("$connection $actualMessage")
            debug("$connection |> ${actualMessage.uri}")
        }
        when (actualMessage.method!!.toUpperCase(Locale.ENGLISH)) {
            "GET" -> myFactory.handleGET(connection, actualMessage.uri!!, actualMessage.isNonPersistent)
            "POST" -> myFactory.handlePOST(connection, actualMessage.uri!!, actualMessage.isNonPersistent, actualMessage.content)
            "OPTIONS" -> myFactory.handleOPTIONS(connection, actualMessage)
            else -> {
                commGorgel.i?.run { info("Received invalid HTTP method ${actualMessage.method} from $connection") }
                connection.close()
            }
        }
    }

    private fun handleStartupTimeout() {
        if (myStartupTimeout != null) {
            myStartupTimeout = null
            myStartupTimeoutTripped = true
            myConnection.close()
        }
    }
}
