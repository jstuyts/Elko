package org.elkoserver.foundation.net.rtcp.server

import org.elkoserver.foundation.byteioframer.rtcp.RtcpRequest
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Message handler for RTCP requests wrapping a message stream.
 *
 * @param myConnection  The connection this is to be a message handler for.
 * @param myFactory  The factory what created this.
 * @param startupTimeoutInterval  How long a new connection is given to do
 * something before kicking them off.
 */
class RtcpMessageHandler(
    private val myConnection: Connection,
    private val myFactory: RtcpMessageHandlerFactory,
    startupTimeoutInterval: Int,
    timer: Timer,
    private val rtcpMessageHandlerCommGorgel: Gorgel
) : MessageHandler {

    /** Timeout for kicking off users who connect and then don't do anything  */
    private var myStartupTimeout: Timeout? = timer.after(
        startupTimeoutInterval.toLong()
    ) { handleStartupTimeout() }

    /** Flag that startup timeout has tripped, to detect late messages.  */
    private var myStartupTimeoutTripped = false

    /**
     * Receive notification that the connection has died.
     *
     * In this case, the connection is a TCP connection supporting RTCP, so it
     * doesn't really matter that it died.  Gratuitous TCP connection drops are
     * actually considered normal in the RTCP world.
     *
     * @param connection The (RTCP over TCP) connection that died.
     * @param reason  Why it died.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        myFactory.tcpConnectionDied(connection, reason)
    }

    /**
     * Handle an incoming message from the connection.
     *
     * Since this is an RTCP connection, the message (as parsed by the
     * RTCPRequest framer) will be an RTCP request.  The nature of the request
     * determines what it is from the perspective of the higher level message
     * stream being supported.
     *
     * @param connection  The connection the message was received on.
     * @param message   The message that was received.  This must be an
     * instance of RTCPRequest.
     */
    override fun processMessage(connection: Connection, message: Any) {
        if (myStartupTimeoutTripped) {
            /* They were kicked off for lacktivity, so ignore the message. */
            return
        }

        myStartupTimeout?.cancel()
        myStartupTimeout = null

        val actualMessage = message as RtcpRequest
        rtcpMessageHandlerCommGorgel.d?.run { debug("$connection $actualMessage") }
        when (actualMessage.verb) {
            RtcpRequest.VERB_START -> myFactory.doStart(connection)
            RtcpRequest.VERB_RESUME -> myFactory.doResume(
                connection, actualMessage.sessionID!!,
                actualMessage.clientRecvSeqNum
            )
            RtcpRequest.VERB_ACK -> myFactory.doAck(connection, actualMessage.clientRecvSeqNum)
            RtcpRequest.VERB_MESSAGE -> myFactory.doMessage(connection, actualMessage)
            RtcpRequest.VERB_END -> myFactory.doEnd(connection)
            RtcpRequest.VERB_ERROR -> myFactory.doError(connection, actualMessage.error!!)
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
