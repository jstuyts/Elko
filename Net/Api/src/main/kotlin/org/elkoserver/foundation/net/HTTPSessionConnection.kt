package org.elkoserver.foundation.net

import org.elkoserver.foundation.run.Queue
import org.elkoserver.foundation.timer.TickNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.time.Clock
import java.util.HashSet

/**
 * An implementation of [Connection] that virtualizes a continuous
 * message session out of a series of transient HTTP connections.
 *
 * Make a new HTTP session connection object for an incoming connection,
 * with a given session ID.
 *
 * @param sessionFactory  Factory for creating HTTP message handler objects
 * @param sessionID  The session ID for the session.
 */
class HTTPSessionConnection internal constructor(
        private val sessionFactory: HTTPMessageHandlerFactory,
        internal val sessionID: Long, timer: Timer, clock: Clock, traceFactory: TraceFactory) : ConnectionBase(sessionFactory.networkManager, clock, traceFactory) {
    /** Trace object for logging message traffic.  */
    private val trMsg: Trace = sessionFactory.httpFramer.msgTrace

    /** Server to client message sequence number.  */
    private var mySelectSequenceNumber: Int

    /** Client to server message sequence number.  */
    private var myXmitSequenceNumber: Int

    /** Queue of outgoing messages awaiting retrieval by the client.  */
    private val myQueue: Queue<Any>

    /** Flag indicating that connection is in the midst of shutting down.  */
    private var amClosing: Boolean

    /** TCP connection for transmitting messages to the client, if a select is
     * currently pending, or null if not.  */
    private var myDownstreamConnection: Connection? = null

    /** Flag that downstream connection, if there is one, is non-persistent.  */
    private var myDownstreamIsNonPersistent = false

    /** HTTP framer to interpret HTTP POSTs and format HTTP replies.  */
    private val myHTTPFramer: HTTPFramer

    /** Clock: ticks watch for expired message selects.  */
    private val mySelectClock: org.elkoserver.foundation.timer.Clock

    /** Clock: ticks watch for dead session.  */
    private val myInactivityClock: org.elkoserver.foundation.timer.Clock

    /** Time that connection started waiting for a message select to be
     * responded to, or 0 if it isn't waiting for that.  */
    private var mySelectWaitStartTime: Long = 0

    /** Last time that there was any traffic on this connection from the user,
     * to enable detection of inactive sessions.  */
    private var myLastActivityTime: Long

    /** Time a select request may sit waiting without sending a response, in
     * milliseconds.  */
    private var mySelectTimeoutInterval: Int

    /** Time a session may sit idle before closing it, in milliseconds.  */
    private var mySessionTimeoutInterval: Int

    /** Open TCP connections associated with this session, for cleanup.  */
    private var myConnections: MutableSet<Connection>

    /**
     * Associate a TCP connection with this session.
     *
     * @param connection  The TCP connection used.
     */
    fun associateTCPConnection(connection: Connection) {
        myConnections.add(connection)
        if (trMsg.debug) {
            trMsg.debugm("associate $connection with $this, count=${myConnections.size}")
        }
    }

    /**
     * Set this session's downstream connection to null, so that it now has no
     * downstream connection.
     */
    private fun clearDownstreamConnection() {
        myDownstreamConnection = null
        myDownstreamIsNonPersistent = false
        mySelectWaitStartTime = 0
    }

    /**
     * Shut down the connection.  Any queued messages will be sent.
     */
    override fun close() {
        if (!amClosing) {
            amClosing = true
            val connectionsToClose: Set<Connection> = myConnections
            myConnections = HashSet()
            connectionsToClose
                    .filter { it !== myDownstreamConnection }
                    .forEach(Connection::close)
            sessionFactory.removeSession(this)
            myInactivityClock.stop()
            mySelectClock.stop()
            mySelectWaitStartTime = 0
            if (myDownstreamConnection != null) {
                myDownstreamIsNonPersistent = true
                sendMsg(theHTTPCloseMarker)
            }
            connectionDied(
                    ConnectionCloseException("Normal HTTP session close"))
        }
    }

    /**
     * Handle loss of an underlying TCP connection.
     *
     * @param connection  The TCP connection that died.
     */
    fun dissociateTCPConnection(connection: Connection) {
        myConnections.remove(connection)
        tcpConnectionDied(connection)
    }

    /**
     * Test if this session has any outbound messages that haven't yet been
     * sent.
     *
     * @return true if there are pending messages to send, false if not
     */
    fun hasOutboundMessages(): Boolean = myQueue.hasMoreElements()

    /**
     * Test if this session currently has a pending select waiting.
     *
     * @return true if this session has an open select request, false if not.
     */
    val isSelectWaiting: Boolean
        get() = myDownstreamConnection != null

    /**
     * Take notice that the client session is still active.
     */
    fun noteClientActivity() {
        if (!amClosing) {
            myLastActivityTime = clock.millis()
        }
    }

    /**
     * React to a clock tick event on the select timeout timer.
     *
     * If there is a pending message select, check to see if the client has
     * waited too long; if so, send an empty reply to the select.
     */
    private fun noticeSelectTick() {
        if (mySelectWaitStartTime != 0L) {
            val now = clock.millis()
            if (now - mySelectWaitStartTime > mySelectTimeoutInterval) {
                mySelectWaitStartTime = 0
                noteClientActivity()
                sendMsg(theTimeoutMarker)
            }
        }
    }

    /**
     * React to a clock tick event on the inactivity timeout timer.
     *
     * Check to see if it has been too long since anything was received from
     * the client; if so, close the session.
     */
    private fun noticeInactivityTick() {
        if (mySelectWaitStartTime == 0L &&
                clock.millis() - myLastActivityTime >
                mySessionTimeoutInterval) {
            if (trMsg.event) {
                trMsg.eventm("$this tick: HTTP session timeout")
            }
            close()
        } else {
            if (trMsg.debug) {
                trMsg.debugm("$this tick: HTTP session waiting")
            }
        }
    }

    /**
     * Wrap a message in the appropriate HTML.
     *
     * @param message  The message itself.
     * @param start  true if this message is the first in a batch.
     * @param end  true if this message is the last in a batch.
     *
     * @return a string containing the appropriate HTML wrapped around
     * 'message'.
     */
    private fun packMessage(message: Any, start: Boolean, end: Boolean): String {
        var actualMessage: Any? = message
        if (start) {
            ++mySelectSequenceNumber
        }
        var sequenceNumber = mySelectSequenceNumber
        if (actualMessage === theTimeoutMarker) {
            actualMessage = null
        } else if (actualMessage === theHTTPCloseMarker) {
            actualMessage = null
            sequenceNumber = -1
        }
        return myHTTPFramer.makeSelectReplySegment(actualMessage, sequenceNumber, start, end)
    }

    /**
     * Handle an /xmit/ request from the client, delivering one or more
     * messages.  The message(s) is (are) placed on the run queue and an
     * appropriate acknowledgement HTTP reply is sent back to the sender.
     *
     * @param connection  The TCP connection the messages were delivered on.
     * @param uri  The components of the requested /xmit/ HTTP URI.
     * @param message  The message body that was delivered.
     */
    fun receiveMessage(connection: Connection, uri: SessionURI, message: String) {
        if (amClosing) {
            connection.close()
        } else {
            noteClientActivity()
            if (trMsg.event) {
                trMsg.msgi("$this:$connection", true, message)
            }
            val reply: String
            reply = if (uri.sequenceNumber != myXmitSequenceNumber) {
                trMsg.errorm("$this expected xmit seq # $myXmitSequenceNumber, got ${uri.sequenceNumber}")
                myHTTPFramer.makeSequenceErrorReply("sequenceError")
            } else {
                val unpacker = myHTTPFramer.postBodyUnpacker(message)
                while (unpacker.hasNext()) {
                    enqueueReceivedMessage(unpacker.next())
                }
                ++myXmitSequenceNumber
                myHTTPFramer.makeXmitReply(myXmitSequenceNumber)
            }
            connection.sendMsg(reply)
        }
    }

    /**
     * Handle a /select/ request from the client, polling for message traffic.
     * If there are any outgoing messages pending in the queue, send them
     * immediately in an HTTP reply.  Otherwise, leave the request open until
     * there is something to send.
     *
     * @param downstreamConnection  TCP connection for delivering messages.
     * @param uri  The components of the requested /select/ HTTP URI.
     * @param nonPersistent  True if HTTP request was flagged non-persistent.
     *
     * @return true if an HTTP reply was sent.
     */
    fun selectMessages(downstreamConnection: Connection, uri: SessionURI,
                       nonPersistent: Boolean): Boolean {
        noteClientActivity()
        return if (uri.sequenceNumber != mySelectSequenceNumber) {
            /* Client did a bad, bad thing. */
            trMsg.errorm("$this expected select seq # $mySelectSequenceNumber, got ${uri.sequenceNumber}")
            downstreamConnection.sendMsg(
                    myHTTPFramer.makeSequenceErrorReply("sequenceError"))
            true
        } else if (myQueue.hasMoreElements()) {
            /* There are messages waiting, so send them. */
            val reply = StringBuilder()
            var start = true
            var end: Boolean
            do {
                val message = myQueue.nextElement()
                end = !myQueue.hasMoreElements()
                if (trMsg.event) {
                    trMsg.msgi("$this:$downstreamConnection", false,
                            message)
                }
                reply.append(packMessage(message, start, end))
                start = false
            } while (!end)
            downstreamConnection.sendMsg(reply.toString())
            clearDownstreamConnection()
            true
        } else if (amClosing) {
            /* Session connection is in the midst of closing, so drop the TCP
               connection. */
            downstreamConnection.close()
            false
        } else {
            /* Nothing to do yet, so block awaiting outbound traffic. */
            myDownstreamConnection = downstreamConnection
            myDownstreamIsNonPersistent = nonPersistent
            mySelectWaitStartTime = clock.millis()
            false
        }
    }

    /**
     * Send a message to the other end of the connection.
     *
     * @param message  The message to be sent.
     */
    override fun sendMsg(message: Any) {
        val currentDownstreamConnection: Connection? = myDownstreamConnection
        if (currentDownstreamConnection != null) {
            /* If there *is* a pending select request, use it to send the
               message immediately. */
            if (trMsg.event) {
                trMsg.msgi("$this:$currentDownstreamConnection", false,
                        message)
            }
            currentDownstreamConnection.sendMsg(packMessage(message, true, true))
            if (myDownstreamIsNonPersistent) {
                val toClose: Connection = currentDownstreamConnection
                clearDownstreamConnection()
                toClose.close()
            } else {
                clearDownstreamConnection()
            }
            noteClientActivity()
        } else {
            /* If there *is not* a pending select request, put the message on
               the outgoing message queue. */
            myQueue.enqueue(message)
        }
    }

    /**
     * Turn debug features for this connection on or off. In the case of an
     * HTTP session, debug mode involves using longer timeouts so that things
     * work on a human time scale when debugging.
     *
     * @param mode  If true, turn debug mode on; if false, turn it off.
     */
    override fun setDebugMode(mode: Boolean) {
        mySelectTimeoutInterval = sessionFactory.selectTimeout(mode)
        mySessionTimeoutInterval = sessionFactory.sessionTimeout(mode)
    }

    /**
     * Handle loss of an underlying TCP connection.  This happens all the time
     * in the HTTP world, and is only a (mild) problem if there was a pending
     * select request open on the connection.
     *
     * @param connection  The TCP connection that died.
     */
    private fun tcpConnectionDied(connection: Connection) {
        if (myDownstreamConnection === connection) {
            clearDownstreamConnection()
            noteClientActivity()
            if (trMsg.event) {
                trMsg.eventm("$this lost $connection with pending select")
            }
        }
    }

    /**
     * Get a printable String representation of this connection.
     *
     * @return a printable representation of this connection.
     */
    override fun toString(): String = "HTTP(${id()},$sessionID)"

    companion object {
        /** Marker on send queue for select timeout.  */
        private val theTimeoutMarker: Any = "(no messages)"

        /** Marker on send queue for connection close.  */
        private val theHTTPCloseMarker: Any = "(end of session)"
    }

    init {
        sessionFactory.addSession(this)
        myConnections = HashSet()
        myLastActivityTime = clock.millis()
        if (trMsg.event) {
            trMsg.eventi("$this new connection")
        }
        mySelectSequenceNumber = 1
        myXmitSequenceNumber = 1
        clearDownstreamConnection()
        myQueue = Queue()
        myHTTPFramer = sessionFactory.httpFramer
        amClosing = false
        mySelectTimeoutInterval = sessionFactory.selectTimeout(false)
        mySelectClock = timer.every((mySelectTimeoutInterval + 1000) / 4.toLong(), object : TickNoticer {
            override fun noticeTick(ticks: Int) {
                noticeSelectTick()
            }
        })
        mySelectClock.start()
        mySessionTimeoutInterval = sessionFactory.sessionTimeout(false)
        myInactivityClock = timer.every(mySessionTimeoutInterval + 1000.toLong(), object : TickNoticer {
            override fun noticeTick(ticks: Int) {
                noticeInactivityTick()
            }
        })
        myInactivityClock.start()
        enqueueHandlerFactory(sessionFactory.innerFactory)
    }
}
