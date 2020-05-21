package org.elkoserver.foundation.net

import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.TraceFactory

/**
 * Message handler for RTCP requests wrapping a message stream.
 *
 * @param myConnection  The connection this is to be a message handler for.
 * @param myFactory  The factory what created this.
 * @param startupTimeoutInterval  How long a new connection is given to do
 * something before kicking them off.
 */
internal class RTCPMessageHandler(
        private val myConnection: Connection,
        private val myFactory: RTCPMessageHandlerFactory,
        startupTimeoutInterval: Int, timer: Timer, private val traceFactory: TraceFactory) : MessageHandler {

    /** Timeout for kicking off users who connect and then don't do anything  */
    private var myStartupTimeout: Timeout?

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
        if (myStartupTimeout != null) {
            myStartupTimeout!!.cancel()
            myStartupTimeout = null
        }
        val actualMessage = message as RTCPRequest
        if (traceFactory.comm.debug) {
            traceFactory.comm.debugm("$connection $actualMessage")
        }
        when (actualMessage.verb) {
            RTCPRequest.VERB_START -> myFactory.doStart(connection)
            RTCPRequest.VERB_RESUME -> myFactory.doResume(connection, actualMessage.sessionID!!,
                    actualMessage.clientRecvSeqNum)
            RTCPRequest.VERB_ACK -> myFactory.doAck(connection, actualMessage.clientRecvSeqNum)
            RTCPRequest.VERB_MESSAGE -> myFactory.doMessage(connection, actualMessage)
            RTCPRequest.VERB_END -> myFactory.doEnd(connection)
            RTCPRequest.VERB_ERROR -> myFactory.doError(connection, actualMessage.error!!)
        }
    }

    init {
        /* Kick the user off if they haven't yet done anything. */
        myStartupTimeout = timer.after(
                startupTimeoutInterval.toLong(),
                object : TimeoutNoticer {
                    override fun noticeTimeout() {
                        handleStartupTimeout()
                    }
                })
    }

    private fun handleStartupTimeout() {
        if (myStartupTimeout != null) {
            myStartupTimeout = null
            myStartupTimeoutTripped = true
            myConnection.close()
        }
    }
}
