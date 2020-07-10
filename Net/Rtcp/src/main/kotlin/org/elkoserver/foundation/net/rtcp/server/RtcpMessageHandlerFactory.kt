package org.elkoserver.foundation.net.rtcp.server

import org.elkoserver.foundation.byteioframer.rtcp.RtcpRequest
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.HashMap

/**
 * Message handler factory to provide message handlers that wrap a message
 * stream inside a series of RTCP requests.
 *
 * RTCP builds a message channel that can span a series of successive TCP
 * connections, allowing a session to be interrupted by connection failure and
 * then resumed without loss of session state.
 *
 * Each RTCP message handler wraps an application-level message handler,
 * which is the entity that will actually process the messages extracted
 * from the RTCP requests, so the RTCP message handler factory needs to
 * wrap the application-level message handler factory.
 *
 * @param innerFactory  The application-level message handler factory that
 * is to be wrapped by this.
 */
class RtcpMessageHandlerFactory(
        internal val innerFactory: MessageHandlerFactory,
        private val gorgel: Gorgel,
        private val rtcpSessionConnectionFactory: RtcpSessionConnectionFactory,
        props: ElkoProperties,
        private val timer: Timer,
        private val rtcpMessageHandlerCommGorgel: Gorgel) : MessageHandlerFactory {

    /** Table of current sessions, indexed by session ID.  */
    private val mySessions = HashMap<String, RtcpSessionConnection>()

    /** Table of current sessions, indexed by TCP connection.  */
    private val mySessionsByConnection = HashMap<Connection, RtcpSessionConnection>()

    /** Time an RTCP session can sit idle before being closed, milliseconds.  */
    private val mySessionInactivityTimeout = props.intProperty("conf.comm.rtcptimeout", DEFAULT_SESSION_INACTIVITY_TIMEOUT) * 1000

    /** Like mySessionInactivityTimeout, but when in debug mode.  */
    private val myDebugSessionInactivityTimeout = props.intProperty("conf.comm.rtcptimeout.debug", DEFAULT_SESSION_INACTIVITY_TIMEOUT) * 1000

    /** Time a session can sit disconnected before being closed, in ms.  */
    private val mySessionDisconnectedTimeout = props.intProperty("conf.comm.rtcpdisconntimeout", DEFAULT_SESSION_DISCONNECTED_TIMEOUT) * 1000

    /** Like mySessionDisconnectedTimeout, but when in debug mode.  */
    private val myDebugSessionDisconnectedTimeout = props.intProperty("conf.comm.rtcpdisconntimeout.debug", DEFAULT_SESSION_DISCONNECTED_TIMEOUT) * 1000

    /** Volume of message backlog an RTCP session can tolerate before being
     * closed, in characters.  */
    internal val sessionBacklogLimit = props.intProperty("conf.comm.rtcpbacklog", DEFAULT_SESSION_BACKLOG_LIMIT)

    /**
     * Associate a particular TCP connection with an RTCP session.
     *
     * @param session  The session.
     * @param connection  The connection to associate with the session.
     */
    private fun acquireTCPConnection(session: RtcpSessionConnection,
                                     connection: Connection) {
        mySessionsByConnection[connection] = session
        session.acquireTCPConnection(connection)
    }

    /**
     * Add a session to the session table.
     *
     * @param session  The session to add.
     */
    fun addSession(session: RtcpSessionConnection) {
        mySessions[session.sessionID] = session
    }

    /**
     * Handle an RTCP 'ack' request, keeping the connection alive and updating
     * the server's picture of which messages the client has received.
     *
     * @param connection  The TCP connection upon which the 'ack' request was
     * received.
     * @param clientRecvSeqNum  The client's received message sequence number
     */
    fun doAck(connection: Connection, clientRecvSeqNum: Int) {
        val session = mySessionsByConnection[connection]
        if (session != null) {
            session.clientAck(clientRecvSeqNum)
        } else {
            val reply = makeErrorReply("noSession")
            sendWithLog(connection, reply)
        }
    }

    /**
     * Handle an RTCP 'end' request, causing the explicit termination of an
     * RTCP session by the client.
     *
     * @param connection  The TCP connection upon which the 'end' request
     * was received.
     */
    fun doEnd(connection: Connection) {
        val session = mySessionsByConnection[connection]
        if (session != null) {
            session.close()
        } else {
            gorgel.error("got RTCP end request on connection with no associated session $connection")
        }
    }

    /**
     * Handle an RTCP 'error' request, which simply announces an error from the
     * client
     * @param connection  The TCP connection upon which the 'error' request was
     * received.
     * @param errorTag  The error tag string from the request
     */
    fun doError(connection: Connection, errorTag: String) {
        gorgel.i?.run { info("$connection received error request $errorTag") }
    }

    /**
     * Handle an RTCP message request, transmitting messages from the client
     * to the server.
     *
     * @param connection  The TCP connection upon which the message(s)
     * was(were) delivered.
     * @param message   The RTCP request descriptor containing the message(s)
     * sent from the client.
     */
    fun doMessage(connection: Connection, message: RtcpRequest) {
        val session = mySessionsByConnection[connection]
        if (session != null) {
            session.receiveMessage(message)
        } else {
            val reply = makeErrorReply("noSession")
            sendWithLog(connection, reply)
        }
    }

    /**
     * Handle an RTCP 'resume' request, causing the resumption of a session
     * previously interrupted by loss of its TCP connection.
     *
     * @param connection  The TCP connection upon which the 'resume' request
     * was received.
     * @param sessionID  The session ID of the session whose resumption is to
     * be attempted.
     */
    fun doResume(connection: Connection, sessionID: String,
                 clientRecvSeqNum: Int) {
        var session = mySessionsByConnection[connection]
        var reply: String? = null
        if (session != null) {
            reply = makeErrorReply("sessionInProgress")
        } else {
            session = getSession(sessionID)
            if (session != null) {
                acquireTCPConnection(session, connection)
                gorgel.i?.run { info("$session resume ${session.sessionID}") }
                sendWithLog(connection,
                        makeResumeReply(session.sessionID,
                                session.clientSendSeqNum))
                session.replayUnacknowledgedMessages(clientRecvSeqNum)
            } else {
                reply = makeErrorReply("noSuchSession")
            }
        }
        reply?.let { sendWithLog(connection, it) }
    }

    /**
     * Handle an RTCP 'start' request, causing the creation of a new session.
     *
     * @param connection  The TCP connection upon which the 'start' request
     * was received.
     */
    fun doStart(connection: Connection) {
        var session = mySessionsByConnection[connection]
        val reply: String
        if (session != null) {
            reply = makeErrorReply("sessionInProgress")
        } else {
            session = rtcpSessionConnectionFactory.create(this)
            acquireTCPConnection(session, connection)
            gorgel.i?.run { info("$session start ${session.sessionID}") }
            reply = makeStartReply(session.sessionID)
        }
        sendWithLog(connection, reply)
    }

    /**
     * Look up the session associated with some session ID.
     *
     * @param sessionID  The ID of the session sought.
     *
     * @return the session whose session ID is 'sessionID', or null if there is
     * no such session.
     */
    private fun getSession(sessionID: String): RtcpSessionConnection? = mySessions[sessionID]

    /**
     * Generate the request line for an RTCP 'ack' request, acknowledging
     * client messages that have been received.
     *
     * @param clientSeqNum  The server's client message sequence number, i.e.,
     * the serial number of the client message most recently received by
     * the server.
     *
     * @return an RTCP 'ack' request line corresponding to the parameter.
     */
    fun makeAck(clientSeqNum: Int): String = "ack $clientSeqNum\n"

    /**
     * Generate the request line for an RTCP 'error' request, typically the
     * error reply to some request received by the server.
     *
     * @param errorTag  The error tag string
     * @return an RTCP 'error' request line corresponding to the parameters.
     */
    fun makeErrorReply(errorTag: String): String = "error $errorTag\n"

    /**
     * Generate the request line for an RTCP message delivery request, sending
     * messages from the server to the client.
     *
     * @param serverSeqNum  The server's message sequence number, i.e., the
     * serial number of the outgoing message.
     * @param clientSeqNum  The server's client message sequence number, i.e.,
     * the serial number of the client message most recently received by
     * the server.
     * @param message  The message itself, pre-encoded as a string (sans
     * framing) by the caller.
     *
     * @return an RTCP message delivery request corresponding to the
     * parameters.
     */
    fun makeMessage(serverSeqNum: Int, clientSeqNum: Int, message: String): String = "$serverSeqNum $clientSeqNum\n$message\n\n"

    /**
     * Generate the request line for an RTCP 'resume' reply.
     *
     * @param sessionID  The session ID of the session being resumed
     * @param seqNum  The server's client message sequence number, i.e., the
     * serial number of the client message most recently received by the
     * server as part of the indicated session.
     *
     * @return an RTCP 'resume' reply line corresponding to the parameters.
     */
    private fun makeResumeReply(sessionID: String, seqNum: Int): String = "resume $sessionID $seqNum\n"

    /**
     * Generate the request line for an RTCP 'start' reply.
     *
     * @param sessionID  The session ID of the session being started.
     *
     * @return an RTCP 'start' reply line corresponding to the parameter.
     */
    private fun makeStartReply(sessionID: String): String = "start $sessionID\n"

    /**
     * Provide a message handler for a new (RTCP over TCP) connection.
     *
     * @param connection  The TCP connection object that was just created.
     */
    override fun provideMessageHandler(connection: Connection?): MessageHandler {
        return RtcpMessageHandler(connection!!, this,
                sessionInactivityTimeout(false), timer, rtcpMessageHandlerCommGorgel)
    }

    /**
     * Remove a session from the session table.
     *
     * @param session  The session to remove.
     */
    fun removeSession(session: RtcpSessionConnection) {
        mySessions.remove(session.sessionID)
    }

    /**
     * Send a message on a connection, with logging.
     *
     * @param connection  Connection to send on
     * @param msg  The message to send.
     */
    private fun sendWithLog(connection: Connection, msg: String) {
        gorgel.d?.run { debug("$connection <| ${msg.trim { it <= ' ' }}") }
        connection.sendMsg(msg)
    }

    /**
     * Obtain the RTCP session disconnected timeout interval: the time an RTCP
     * session can be without a live TCP connection before the server decides
     * that the user isn't coming back and closing the session.
     *
     * @param debug  If true, return the debug-mode timeout; if false, return
     * the normal use timeout.
     *
     * @return the session disconnected timeout interval, in milliseconds.
     */
    fun sessionDisconnectedTimeout(debug: Boolean): Int {
        return if (debug) {
            myDebugSessionDisconnectedTimeout
        } else {
            mySessionDisconnectedTimeout
        }
    }

    /**
     * Obtain the RTCP session inactivity timeout interval: the time an RTCP
     * session can be idle before the server closes it.
     *
     * @param debug  If true, return the debug-mode timeout; if false, return
     * the normal use timeout.
     *
     * @return the session inactivity timeout interval, in milliseconds.
     */
    fun sessionInactivityTimeout(debug: Boolean): Int {
        return if (debug) {
            myDebugSessionInactivityTimeout
        } else {
            mySessionInactivityTimeout
        }
    }

    /**
     * Receive notification that an underlying TCP connection has died.
     *
     * @param connection  The TCP connection that died.
     * @param reason  The reason why.
     */
    fun tcpConnectionDied(connection: Connection, reason: Throwable) {
        val session = mySessionsByConnection.remove(connection)
        if (session != null) {
            session.loseTCPConnection(connection)
            gorgel.i?.run { info("$connection lost under $session-${session.sessionID}: $reason") }
        } else {
            gorgel.i?.run { info("$connection lost under no known RTCP session: $reason") }
        }
    }

    companion object {
        /** Default inactivity timeout if none is explicitly given, in seconds.  */
        private const val DEFAULT_SESSION_INACTIVITY_TIMEOUT = 60

        /** Default disconnected timeout if none is explicitly given, in secs.  */
        private const val DEFAULT_SESSION_DISCONNECTED_TIMEOUT = 30

        /** Default message backlog limit if none explicitly given, in chars.  */
        private const val DEFAULT_SESSION_BACKLOG_LIMIT = 64000
    }
}