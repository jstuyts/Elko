package org.elkoserver.foundation.net.http.server

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.ConnectionBase
import org.elkoserver.foundation.net.ConnectionCloseException
import org.elkoserver.foundation.net.LoadMonitor
import org.elkoserver.foundation.net.SessionUri
import org.elkoserver.foundation.timer.TickNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.slf4j.Gorgel
import java.time.Clock
import java.util.ArrayDeque
import java.util.concurrent.Executor

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
class HttpSessionConnection internal constructor(
    private val sessionFactory: HttpMessageHandlerFactory,
    private val gorgel: Gorgel,
    runner: Executor,
    loadMonitor: LoadMonitor,
    internal val sessionID: Long, timer: Timer, clock: Clock, commGorgel: Gorgel, idGenerator: IdGenerator)
    : ConnectionBase(runner, loadMonitor, clock, commGorgel, idGenerator) {

    /** Server to client message sequence number.  */
    private var mySelectSequenceNumber = 1

    /** Client to server message sequence number.  */
    private var myXmitSequenceNumber = 1

    /** Queue of outgoing messages awaiting retrieval by the client.  */
    private val myQueue = ArrayDeque<Any>()

    /** Flag indicating that connection is in the midst of shutting down.  */
    private var amClosing = false

    /** TCP connection for transmitting messages to the client, if a select is
     * currently pending, or null if not.  */
    private var myDownstreamConnection: Connection? = null

    /** Flag that downstream connection, if there is one, is non-persistent.  */
    private var myDownstreamIsNonPersistent = false

    /** HTTP framer to interpret HTTP POSTs and format HTTP replies.  */
    private val myHttpFramer = sessionFactory.httpFramer

    /** Time a select request may sit waiting without sending a response, in
     * milliseconds.  */
    private var mySelectTimeoutInterval = sessionFactory.selectTimeout(false)

    /** Time a session may sit idle before closing it, in milliseconds.  */
    private var mySessionTimeoutInterval = sessionFactory.sessionTimeout(false)

    /** Clock: ticks watch for expired message selects.  */
    private val mySelectClock = timer.every((mySelectTimeoutInterval + 1000) / 4.toLong(), object : TickNoticer {
        override fun noticeTick(ticks: Int) {
            noticeSelectTick()
        }
    }).apply(org.elkoserver.foundation.timer.Clock::start)

    /** Clock: ticks watch for dead session.  */
    private val myInactivityClock = timer.every(mySessionTimeoutInterval + 1000.toLong(), object : TickNoticer {
        override fun noticeTick(ticks: Int) {
            noticeInactivityTick()
        }
    }).apply(org.elkoserver.foundation.timer.Clock::start)

    /** Time that connection started waiting for a message select to be
     * responded to, or 0 if it isn't waiting for that.  */
    private var mySelectWaitStartTime: Long = 0

    /** Last time that there was any traffic on this connection from the user,
     * to enable detection of inactive sessions.  */
    private var myLastActivityTime = clock.millis()

    /** Open TCP connections associated with this session, for cleanup.  */
    private var myConnections = mutableSetOf<Connection>()

    /**
     * Associate a TCP connection with this session.
     *
     * @param connection  The TCP connection used.
     */
    fun associateTCPConnection(connection: Connection) {
        myConnections.add(connection)
        gorgel.d?.run { debug("associate $connection with ${this@HttpSessionConnection}, count=${myConnections.size}") }
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
            myConnections = mutableSetOf()
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
                    ConnectionCloseException("Normal HTTP session close")
            )
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
    fun hasOutboundMessages(): Boolean = myQueue.isNotEmpty()

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
            gorgel.i?.run { info("${this@HttpSessionConnection} tick: HTTP session timeout") }
            close()
        } else {
            gorgel.d?.run { debug("${this@HttpSessionConnection} tick: HTTP session waiting") }
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
        return myHttpFramer.makeSelectReplySegment(actualMessage, sequenceNumber, start, end)
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
    fun receiveMessage(connection: Connection, uri: SessionUri, message: String) {
        if (amClosing) {
            connection.close()
        } else {
            noteClientActivity()
            gorgel.i?.run { info("${this@HttpSessionConnection}:$connection -> $message") }
            val reply = if (uri.sequenceNumber != myXmitSequenceNumber) {
                gorgel.error("$this expected xmit seq # $myXmitSequenceNumber, got ${uri.sequenceNumber}")
                myHttpFramer.makeSequenceErrorReply("sequenceError")
            } else {
                val unpacker = myHttpFramer.postBodyUnpacker(message)
                while (unpacker.hasNext()) {
                    enqueueReceivedMessage(unpacker.next())
                }
                ++myXmitSequenceNumber
                myHttpFramer.makeXmitReply(myXmitSequenceNumber)
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
    fun selectMessages(downstreamConnection: Connection, uri: SessionUri,
                       nonPersistent: Boolean): Boolean {
        noteClientActivity()
        return when {
            uri.sequenceNumber != mySelectSequenceNumber -> {
                /* Client did a bad, bad thing. */
                gorgel.error("$this expected select seq # $mySelectSequenceNumber, got ${uri.sequenceNumber}")
                downstreamConnection.sendMsg(
                        myHttpFramer.makeSequenceErrorReply("sequenceError"))
                true
            }
            myQueue.isNotEmpty() -> {
                /* There are messages waiting, so send them. */
                val reply = StringBuilder()
                var start = true
                var end: Boolean
                do {
                    val message = myQueue.remove()
                    end = myQueue.isEmpty()
                    gorgel.i?.run { info("${this@HttpSessionConnection}:$downstreamConnection <- $message") }
                    reply.append(packMessage(message, start, end))
                    start = false
                } while (!end)
                downstreamConnection.sendMsg(reply.toString())
                clearDownstreamConnection()
                true
            }
            amClosing -> {
                /* Session connection is in the midst of closing, so drop the TCP
               connection. */
                downstreamConnection.close()
                false
            }
            else -> {
                /* Nothing to do yet, so block awaiting outbound traffic. */
                myDownstreamConnection = downstreamConnection
                myDownstreamIsNonPersistent = nonPersistent
                mySelectWaitStartTime = clock.millis()
                false
            }
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
            gorgel.i?.run { info("${this@HttpSessionConnection}:$currentDownstreamConnection <- $message") }
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
            myQueue.add(message)
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
            gorgel.i?.run { info("${this@HttpSessionConnection} lost $connection with pending select") }
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
        gorgel.i?.run { info("${this@HttpSessionConnection} new connection") }
        clearDownstreamConnection()
        enqueueHandlerFactory(sessionFactory.innerFactory)
    }
}
