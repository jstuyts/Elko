package org.elkoserver.foundation.net.rtcp.server

import org.elkoserver.foundation.byteioframer.rtcp.RtcpRequest
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.ConnectionBase
import org.elkoserver.foundation.net.ConnectionCloseException
import org.elkoserver.foundation.net.LoadMonitor
import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.json.JsonLiteral
import org.elkoserver.util.trace.slf4j.Gorgel
import java.time.Clock
import java.util.LinkedList
import java.util.concurrent.Executor

/**
 * An implementation of [Connection] that virtualizes a continuous
 * message session out of a series of potentially transient TCP connections.
 *
 * @param mySessionFactory  Factory for creating RTCP message handler objects
 * @param sessionIDAsLong  The session ID for the session.
 */
class RtcpSessionConnection internal constructor(
    private val mySessionFactory: RtcpMessageHandlerFactory,
    runner: Executor,
    loadMonitor: LoadMonitor,
    sessionIDAsLong: Long,
    private val timer: Timer,
    clock: Clock,
    private val gorgel: Gorgel,
    commGorgel: Gorgel,
    idGenerator: IdGenerator
) : ConnectionBase(runner, loadMonitor, clock, commGorgel, idGenerator) {

    /** Sequence number of last client->server message received here.  */
    internal var clientSendSeqNum: Int = 0
        private set

    /** Sequence number of last server->client message sent from here.  */
    private var myServerSendSeqNum: Int = 0

    /** Queue of outgoing messages not yet ack'd by the client.  */
    private val myQueue: LinkedList<RtcpMessage> = LinkedList()

    /** Current volume of unacknowledged messages in the outgoing queue.  */
    private var myQueueBacklog: Int = 0

    /** Flag indicating that connection is in the midst of shutting down.  */
    private var amClosing: Boolean = false

    /** TCP connection for transmitting messages to the client, if a connection
     * is currently open, or null if not.  */
    private var myLiveConnection: Connection? = null

    /** Time a session may sit idle before closing it, in milliseconds.  */
    private var myInactivityTimeoutInterval = mySessionFactory.sessionInactivityTimeout(false)

    /** Clock: ticks watch for inactive (and thus presumed dead) session.  */
    private val myInactivityClock = timer.every(
        myInactivityTimeoutInterval / 2 + 1000.toLong()
    ) { noticeInactivityTick() }.apply(org.elkoserver.foundation.timer.Clock::start)

    /** Timeout for closing an abandoned session.  */
    private var myDisconnectedTimeout: Timeout? = null

    /** Last time that there was any traffic on this connection from the user,
     * to enable detection of inactive sessions.  */
    private var myLastActivityTime: Long = clock.millis()

    /** Time a session may sit disconnected before closing it, milliseconds.  */
    private var myDisconnectedTimeoutInterval = mySessionFactory.sessionDisconnectedTimeout(false)

    /** Session ID -- a swiss number to authenticate client RTCP requests.  */
    internal val sessionID: String = sessionIDAsLong.toString()

    /**
     * Associate a TCP connection with this session.
     *
     * @param connection  The TCP connection used.
     */
    fun acquireTCPConnection(connection: Connection) {
        myLiveConnection?.close()
        myLiveConnection = connection
        myDisconnectedTimeout?.cancel()
        myDisconnectedTimeout = null
        commGorgel.d?.run { debug("acquire $connection for ${this@RtcpSessionConnection}") }
    }

    /**
     * Accept an 'ack' message from the client, acknowledging receipt of one
     * or more messages, and providing activity to keep the session alive.
     *
     * @param clientRecvSeqNum  The message number being acknowledged.
     */
    fun clientAck(clientRecvSeqNum: Int) {
        val timeInactive = clock.millis() - myLastActivityTime
        noteClientActivity()
        commGorgel.d?.run { debug("${this@RtcpSessionConnection} ack $clientRecvSeqNum") }
        discardAcknowledgedMessages(clientRecvSeqNum)
        if (timeInactive > myInactivityTimeoutInterval / 4) {
            val ack = mySessionFactory.makeAck(clientSendSeqNum)
            sendMsg(ack)
        }
    }

    /**
     * Shut down the connection.
     */
    override fun close() {
        if (!amClosing) {
            amClosing = true
            mySessionFactory.removeSession(this)
            myInactivityClock.stop()
            myLiveConnection?.close()
            connectionDied(ConnectionCloseException("Normal RTCP session close"))
        }
    }

    /**
     * Once a message from the server to the client has been acknowledged by
     * the client, we needn't retain a copy of it for retransmission.  This
     * method discards any retained messages whose sequence numbers are less
     * than or equal to the parameter.
     *
     * @param seqNum  The highest numbered acknowledged message.
     */
    private fun discardAcknowledgedMessages(seqNum: Int) {
        while (true) {
            val peek = myQueue.peek()
            if (peek == null || peek.seqNum > seqNum) {
                break
            }
            myQueueBacklog -= peek.message.length()
            myQueue.remove()
        }
        commGorgel.d?.run { debug("${this@RtcpSessionConnection} queue backlog decreased to $myQueueBacklog") }
    }

    /**
     * Handle loss of an underlying TCP connection.
     *
     * @param connection  The TCP connection that died.
     */
    fun loseTCPConnection(connection: Connection) {
        if (myLiveConnection === connection) {
            myLiveConnection = null
            tcpConnectionDied(connection)
            myDisconnectedTimeout = timer.after(
                myDisconnectedTimeoutInterval.toLong()
            ) { noticeDisconnectedTimeout() }
        }
    }

    /**
     * Handle the expiration of the disconnected timer: if too much time has
     * passed in a disconnected state, presume that the session is lost and
     * won't be coming back.  Close the connection, if it isn't already dead for
     * other reasons.
     */
    private fun noticeDisconnectedTimeout() {
        if (!amClosing && myLiveConnection == null) {
            commGorgel.i?.run { info("${this@RtcpSessionConnection}: disconnected session timeout") }
            close()
        }
    }

    /**
     * Take notice that the client session is still active.  Since we timeout
     * the session it is inactive for too long, it's good to notice activity.
     */
    private fun noteClientActivity() {
        if (!amClosing) {
            myLastActivityTime = clock.millis()
        }
    }

    /**
     * React to a clock tick event on the inactivity timeout timer.
     *
     * Check to see if it has been too long since anything was received from
     * the client; if so, close the session.
     */
    private fun noticeInactivityTick() {
        val timeInactive = clock.millis() - myLastActivityTime
        when {
            timeInactive > myInactivityTimeoutInterval -> {
                commGorgel.i?.run { info("${this@RtcpSessionConnection} tick: RTCP session timeout") }
                close()
            }
            timeInactive > myInactivityTimeoutInterval / 2 -> {
                commGorgel.d?.run { debug("${this@RtcpSessionConnection} tick: RTCP session acking") }
                val ack = mySessionFactory.makeAck(clientSendSeqNum)
                sendMsg(ack)
            }
            else -> commGorgel.d?.run { debug("${this@RtcpSessionConnection} tick: RTCP session waiting") }
        }
    }

    /**
     * Accept a message or messages delivered from the client.
     *
     * @param request  The RTCP request containing the message bundle to be
     * processed.
     */
    fun receiveMessage(request: RtcpRequest) {
        noteClientActivity()
        if (request.clientSendSeqNum != clientSendSeqNum + 1) {
            commGorgel.error("$this expected client seq # ${clientSendSeqNum + 1}, got ${request.clientSendSeqNum}")
            val reply = mySessionFactory.makeErrorReply("sequenceError")
            sendMsg(reply)
        } else {
            discardAcknowledgedMessages(request.clientRecvSeqNum)
            var message = request.nextMessage()
            while (message != null) {
                gorgel.i?.run { info("${this@RtcpSessionConnection} -> $message") }
                enqueueReceivedMessage(message)
                message = request.nextMessage()
            }
            ++clientSendSeqNum
        }
    }

    /**
     * Resend messages to the client that were previously sent but which the
     * client has not acknowledged.
     *
     * @param seqNum  The sequence number of the most recently acknowledged
     * message.
     */
    fun replayUnacknowledgedMessages(seqNum: Int) {
        discardAcknowledgedMessages(seqNum)
        for (elem in myQueue) {
            val messageString = mySessionFactory.makeMessage(
                elem.seqNum,
                clientSendSeqNum,
                elem.message.sendableString()
            )
            commGorgel.d?.run { debug("${this@RtcpSessionConnection} resend ${elem.seqNum}") }
            myLiveConnection!!.sendMsg(messageString)
        }
    }

    /**
     * Send a message to the other end of the connection.
     *
     * @param message  The message to be sent.  In this version, this must be a
     * String or a JSONLiteral.
     */
    override fun sendMsg(message: Any) {
        if (amClosing) {
            return
        }
        val messageString: String
        if (message is JsonLiteral) {
            ++myServerSendSeqNum
            val qMsg = RtcpMessage(myServerSendSeqNum, message)
            myQueueBacklog += message.length()
            commGorgel.d?.run { debug("${this@RtcpSessionConnection} queue backlog increased to $myQueueBacklog") }
            if (myQueueBacklog > mySessionFactory.sessionBacklogLimit) {
                commGorgel.i?.run { info("${this@RtcpSessionConnection} queue backlog limit exceeded") }
                close()
            }
            myQueue.addLast(qMsg)
            messageString = mySessionFactory.makeMessage(
                myServerSendSeqNum,
                clientSendSeqNum,
                message.sendableString()
            )
            gorgel.d?.run { debug("$myLiveConnection <| $myServerSendSeqNum $clientSendSeqNum") }
            gorgel.i?.run { info("${this@RtcpSessionConnection} <- $message") }
        } else if (message is String) {
            messageString = message
            if (myLiveConnection != null) {
                gorgel.d?.run { debug("$myLiveConnection <| ${messageString.trim { it <= ' ' }}") }
            }
        } else {
            throw IllegalArgumentException("Invalid message type: ${message.javaClass.name}")
        }
        myLiveConnection?.sendMsg(messageString)
    }

    /**
     * Turn debug features for this connection on or off. In the case of an
     * RTCP session, debug mode involves using longer timeouts so that things
     * work on a human time scale when debugging.
     *
     * @param mode  If true, turn debug mode on; if false, turn it off.
     */
    override fun setDebugMode(mode: Boolean) {
        myInactivityTimeoutInterval = mySessionFactory.sessionInactivityTimeout(mode)
        myDisconnectedTimeoutInterval = mySessionFactory.sessionDisconnectedTimeout(mode)
    }

    /**
     * Handle loss of the underlying TCP connection.
     *
     * @param connection  The TCP connection that died.
     */
    private fun tcpConnectionDied(connection: Connection) {
        if (myLiveConnection === connection) {
            myLiveConnection = null
            noteClientActivity()
            commGorgel.i?.run { info("${this@RtcpSessionConnection} lost $connection") }
        }
    }

    /**
     * Get a printable String representation of this connection.
     *
     * @return a printable representation of this connection.
     */
    override fun toString(): String {
        val tag = if (myLiveConnection != null) {
            myLiveConnection.toString()
        } else {
            "*"
        }
        return "RTCP(${id()},$tag)"
    }

    /**
     * Simple struct to hold outgoing messages along with their sequence
     * numbers in the message replay queue.
     */
    private class RtcpMessage(var seqNum: Int, var message: JsonLiteral)

    init {
        mySessionFactory.addSession(this)
        commGorgel.i?.run { info("${this@RtcpSessionConnection} new connection session $sessionID") }
        enqueueHandlerFactory(mySessionFactory.innerFactory)
    }
}
