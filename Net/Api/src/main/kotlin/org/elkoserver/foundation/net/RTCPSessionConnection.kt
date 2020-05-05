package org.elkoserver.foundation.net

import org.elkoserver.foundation.timer.TickNoticer
import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.json.JSONLiteral
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.security.SecureRandom
import java.time.Clock
import java.util.LinkedList
import kotlin.math.abs

/**
 * An implementation of [Connection] that virtualizes a continuous
 * message session out of a series of potentially transient TCP connections.
 *
 * @param mySessionFactory  Factory for creating RTCP message handler objects
 * @param sessionID  The session ID for the session.
 */
class RTCPSessionConnection private constructor(
        private val mySessionFactory: RTCPMessageHandlerFactory,
        sessionID: Long, private val timer: Timer, clock: Clock, traceFactory: TraceFactory) : ConnectionBase(mySessionFactory.networkManager(), clock, traceFactory) {
    /** Trace object for logging message traffic.  */
    private val trMsg: Trace

    /** Sequence number of last client->server message received here.  */
    private var myClientSendSeqNum: Int

    /** Sequence number of last server->client message sent from here.  */
    private var myServerSendSeqNum: Int

    /** Queue of outgoing messages not yet ack'd by the client.  */
    private val myQueue: LinkedList<RTCPMessage>

    /** Current volume of unacknowledged messages in the outgoing queue.  */
    private var myQueueBacklog: Int

    /** Flag indicating that connection is in the midst of shutting down.  */
    private var amClosing: Boolean

    /** TCP connection for transmitting messages to the client, if a connection
     * is currently open, or null if not.  */
    private var myLiveConnection: Connection? = null

    /** Clock: ticks watch for inactive (and thus presumed dead) session.  */
    private val myInactivityClock: org.elkoserver.foundation.timer.Clock

    /** Timeout for killing an abandoned session.  */
    private var myDisconnectedTimeout: Timeout?

    /** Last time that there was any traffic on this connection from the user,
     * to enable detection of inactive sessions.  */
    private var myLastActivityTime: Long

    /** Time a session may sit idle before killing it, in milliseconds.  */
    private var myInactivityTimeoutInterval: Int

    /** Time a session may sit disconnected before killing it, milliseconds.  */
    private var myDisconnectedTimeoutInterval: Int

    /** Session ID -- a swiss number to authenticate client RTCP requests.  */
    private val mySessionID: String

    /**
     * Make a new RTCP session connection object for an incoming connection,
     * with a new, internally generated, session ID.
     *
     * @param sessionFactory  Factory for creating RTCP message handler objects
     */
    internal constructor(sessionFactory: RTCPMessageHandlerFactory, timer: Timer, clock: Clock, traceFactory: TraceFactory) : this(sessionFactory, abs(theRandom.nextLong()), timer, clock, traceFactory) {}

    /**
     * Associate a TCP connection with this session.
     *
     * @param connection  The TCP connection used.
     */
    fun acquireTCPConnection(connection: Connection) {
        if (myLiveConnection != null) {
            myLiveConnection!!.close()
        }
        myLiveConnection = connection
        if (myDisconnectedTimeout != null) {
            myDisconnectedTimeout!!.cancel()
            myDisconnectedTimeout = null
        }
        if (traceFactory.comm.debug) {
            traceFactory.comm.debugm("acquire $connection for $this")
        }
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
        if (traceFactory.comm.debug) {
            traceFactory.comm.debugm("$this ack $clientRecvSeqNum")
        }
        discardAcknowledgedMessages(clientRecvSeqNum)
        if (timeInactive > myInactivityTimeoutInterval / 4) {
            val ack = mySessionFactory.makeAck(myClientSendSeqNum)
            sendMsg(ack)
        }
    }

    /**
     * Obtain this connection's client send sequence number, the sequence
     * number of the most recent client to server message received by the
     * server.
     *
     * @return this connection's client send sequence number.
     */
    fun clientSendSeqNum(): Int {
        return myClientSendSeqNum
    }

    /**
     * Shut down the connection.
     */
    override fun close() {
        if (!amClosing) {
            amClosing = true
            mySessionFactory.removeSession(this)
            myInactivityClock.stop()
            if (myLiveConnection != null) {
                myLiveConnection!!.close()
            }
            connectionDied(
                    ConnectionCloseException("Normal RTCP session close"))
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
        if (traceFactory.comm.debug) {
            traceFactory.comm.debugm("$this queue backlog decreased to $myQueueBacklog")
        }
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
            myDisconnectedTimeout = timer.after(myDisconnectedTimeoutInterval.toLong(), object : TimeoutNoticer {
                override fun noticeTimeout() {
                    noticeDisconnectedTimeout()
                }
            })
        }
    }

    /**
     * Handle the expiration of the disconnected timer: if too much time has
     * passed in a disconnected state, presume that the session is lost and
     * won't be coming back.  Kill the connection, if it isn't already dead for
     * other reasons.
     */
    private fun noticeDisconnectedTimeout() {
        if (!amClosing && myLiveConnection == null) {
            if (traceFactory.comm.event) {
                traceFactory.comm.eventm("$this: disconnected session timeout")
            }
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
     * the client; if so, kill the session.
     */
    private fun noticeInactivityTick() {
        val timeInactive = clock.millis() - myLastActivityTime
        if (timeInactive > myInactivityTimeoutInterval) {
            if (traceFactory.comm.event) {
                traceFactory.comm.eventm("$this tick: RTCP session timeout")
            }
            close()
        } else if (timeInactive > myInactivityTimeoutInterval / 2) {
            if (traceFactory.comm.debug) {
                traceFactory.comm.debugm("$this tick: RTCP session acking")
            }
            val ack = mySessionFactory.makeAck(myClientSendSeqNum)
            sendMsg(ack)
        } else {
            if (traceFactory.comm.debug) {
                traceFactory.comm.debugm("$this tick: RTCP session waiting")
            }
        }
    }

    /**
     * Accept a message or messages delivered from the client.
     *
     * @param request  The RTCP request containing the message bundle to be
     * processed.
     */
    fun receiveMessage(request: RTCPRequest) {
        noteClientActivity()
        if (request.clientSendSeqNum() != myClientSendSeqNum + 1) {
            traceFactory.comm.errorm(this.toString() + " expected client seq # " +
                    (myClientSendSeqNum + 1) + ", got " +
                    request.clientSendSeqNum())
            val reply = mySessionFactory.makeErrorReply("sequenceError")
            sendMsg(reply)
        } else {
            discardAcknowledgedMessages(request.clientRecvSeqNum())
            var message = request.nextMessage()
            while (message != null) {
                if (trMsg.event) {
                    trMsg.msgi(this, true, message)
                }
                enqueueReceivedMessage(message)
                message = request.nextMessage()
            }
            ++myClientSendSeqNum
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
            val messageString = mySessionFactory.makeMessage(elem.seqNum,
                    myClientSendSeqNum,
                    elem.message.sendableString())
            if (traceFactory.comm.debug) {
                traceFactory.comm.debugm(this.toString() + " resend " + elem.seqNum)
            }
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
        if (message is JSONLiteral) {
            val jsonMessage = message
            ++myServerSendSeqNum
            val qMsg = RTCPMessage(myServerSendSeqNum, jsonMessage)
            myQueueBacklog += jsonMessage.length()
            if (traceFactory.comm.debug) {
                traceFactory.comm.debugm("$this queue backlog increased to $myQueueBacklog")
            }
            if (myQueueBacklog > mySessionFactory.sessionBacklogLimit()) {
                traceFactory.comm.eventm("$this queue backlog limit exceeded")
                close()
            }
            myQueue.addLast(qMsg)
            messageString = mySessionFactory.makeMessage(myServerSendSeqNum,
                    myClientSendSeqNum,
                    jsonMessage.sendableString())
            if (trMsg.debug) {
                trMsg.debugm(myLiveConnection.toString() + " <| " + myServerSendSeqNum + " " + myClientSendSeqNum)
            }
            if (trMsg.event) {
                trMsg.msgi(this, false, message)
            }
        } else if (message is String) {
            messageString = message
            if (myLiveConnection != null) {
                if (trMsg.debug) {
                    trMsg.debugm(myLiveConnection.toString() + " <| " +
                            messageString.trim { it <= ' ' })
                }
            }
        } else {
            throw IllegalArgumentException("Invalid message type: ${message.javaClass.name}")
        }
        if (myLiveConnection != null) {
            myLiveConnection!!.sendMsg(messageString)
        }
    }

    /**
     * Obtain this connection's session ID.
     *
     * @return the session ID number of this session.
     */
    fun sessionID(): String {
        return mySessionID
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
            if (traceFactory.comm.event) {
                traceFactory.comm.eventm("$this lost $connection")
            }
        }
    }

    /**
     * Get a printable String representation of this connection.
     *
     * @return a printable representation of this connection.
     */
    override fun toString(): String {
        val tag: String
        tag = if (myLiveConnection != null) {
            myLiveConnection.toString()
        } else {
            "*"
        }
        return "RTCP(" + id() + "," + tag + ")"
    }

    /**
     * Simple struct to hold outgoing messages along with their sequence
     * numbers in the message replay queue.
     */
    private class RTCPMessage internal constructor(var seqNum: Int, var message: JSONLiteral)

    companion object {
        /** Random number generator, for creating session IDs.  */
        @Deprecated("Global variable")
        private val theRandom = SecureRandom()

        /**
         * Force initialization of the secure random number generator.
         *
         * This is a kludge motivated by said initialization being very slow.
         * Ideally, any long initialization delay ought to happen at system startup
         * time, before anybody is using the system who would care.  However,
         * Java's random number generator uses lazy initialization and won't
         * actually initialize itself until the first time it is used.  In ordinary
         * use, that would be the first time somebody tried to connect.  Users
         * shouldn't be subjected to random, mysterious long delays, so generating
         * one gratuitous random number here forces the initialization cost to be
         * paid at startup time as was desired.
         */
        @JvmStatic
        fun initializeRNG() {
            /* Get the initialization delay over right now */
            theRandom.nextBoolean()
        }
    }

    init {
        this.traceFactory = traceFactory
        trMsg = mySessionFactory.msgTrace()
        mySessionID = "" + sessionID
        mySessionFactory.addSession(this)
        myLastActivityTime = clock.millis()
        if (traceFactory.comm.event) {
            traceFactory.comm.eventi("$this new connection session $mySessionID")
        }
        myServerSendSeqNum = 0
        myClientSendSeqNum = 0
        myQueue = LinkedList()
        myQueueBacklog = 0
        amClosing = false
        myDisconnectedTimeout = null
        myDisconnectedTimeoutInterval = mySessionFactory.sessionDisconnectedTimeout(false)
        myInactivityTimeoutInterval = mySessionFactory.sessionInactivityTimeout(false)
        myInactivityClock = timer.every(myInactivityTimeoutInterval / 2 + 1000.toLong(), object : TickNoticer {
            override fun noticeTick(ticks: Int) {
                noticeInactivityTick()
            }
        })
        myInactivityClock.start()
        enqueueHandlerFactory(mySessionFactory.innerFactory())
    }
}
