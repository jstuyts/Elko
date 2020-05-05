package org.elkoserver.foundation.net

import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.TraceFactory

/**
 * Message handler for RTCP requests wrapping a message stream.
 */
internal class RTCPMessageHandler(
        /** The connection this handler handles messages for.  */
        private val myConnection: Connection,
        /** The factory that created this handler, which also contains much of the
         * handler implementation logic.  */
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
     * @param rawMessage   The message that was received.  This must be an
     * instance of RTCPRequest.
     */
    override fun processMessage(connection: Connection, rawMessage: Any) {
        if (myStartupTimeoutTripped) {
            /* They were kicked off for lacktivity, so ignore the message. */
            return
        }
        if (myStartupTimeout != null) {
            myStartupTimeout!!.cancel()
            myStartupTimeout = null
        }
        val message = rawMessage as RTCPRequest
        if (traceFactory.comm.debug) {
            traceFactory.comm.debugm("$connection $message")
        }
        when (message.verb()) {
            RTCPRequest.VERB_START -> myFactory.doStart(connection)
            RTCPRequest.VERB_RESUME -> myFactory.doResume(connection, message.sessionID()!!,
                    message.clientRecvSeqNum())
            RTCPRequest.VERB_ACK -> myFactory.doAck(connection, message.clientRecvSeqNum())
            RTCPRequest.VERB_MESSAGE -> myFactory.doMessage(connection, message)
            RTCPRequest.VERB_END -> myFactory.doEnd(connection)
            RTCPRequest.VERB_ERROR -> myFactory.doError(connection, message.error()!!)
        }
    }

    /**
     * Constructor.
     *
     * @param connection  The connection this is to be a message handler for.
     * @param factory  The factory what created this.
     * @param startupTimeoutInterval  How long a new connection is given to do
     * something before kicking them off.
     */
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
