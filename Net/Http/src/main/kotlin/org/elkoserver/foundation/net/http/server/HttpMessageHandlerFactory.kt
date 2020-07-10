package org.elkoserver.foundation.net.http.server

import org.elkoserver.foundation.byteioframer.http.HttpError
import org.elkoserver.foundation.byteioframer.http.HttpOptionsReply
import org.elkoserver.foundation.byteioframer.http.HttpRequest
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.SessionUri
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.HashMap

/**
 * Message handler factory to provide message handlers that wrap a message
 * stream inside a series of HTTP requests.
 *
 * The challenge is that HTTP can't be relied on to hold open a single TCP
 * connection continuously, even though that's the desired abstraction.
 * Instead, a series of potentially short-lived HTTP over TCP connections need
 * to be turned into a single seamless message stream.  The correlation between
 * HTTP requests and their associated message connections is done via swiss
 * numbers in the URLs.  That job is done by HTTPMessageHandler objects, which
 * are dispensed here, and their associated HTTPSessionConnection objects.
 *
 * Each HTTP message handler wraps an application-level message handler,
 * which is the entity that will actually process the messages extracted
 * from the HTTP requests, so the HTTP message handler factory needs to
 * wrap the application-level message handler factory.
 *
 * @param innerFactory  The application-level message handler factor that
 * is to be wrapped by this.
 * @param rootUri  The root URI for GETs and POSTs.
 * @param httpFramer  HTTP framer to interpret HTTP POSTs and format HTTP
 * replies.
 */
class HttpMessageHandlerFactory internal constructor(
        internal val innerFactory: MessageHandlerFactory,
        rootUri: String,
        internal val httpFramer: HttpFramer,
        props: ElkoProperties,
        private val timer: Timer,
        private val handlerCommGorgel: Gorgel,
        private val handlerFactoryCommGorgel: Gorgel,
        private val httpSessionConnectionFactory: HttpSessionConnectionFactory) : MessageHandlerFactory {

    /** The root URI for GETs and POSTs.  */
    private val myRootUri: String = "/$rootUri/"

    /** Table of current sessions, indexed by ID number.  */
    private val mySessions: MutableMap<Long, HttpSessionConnection> = HashMap()

    /** Table of current sessions, indexed by TCP connection.  */
    private val mySessionsByConnection: MutableMap<Connection, HttpSessionConnection> = HashMap()

    /** Time an HTTP select request can wait before it must be responded to, in
     * milliseconds.  */
    private val mySelectTimeout = props.intProperty("conf.comm.httpselectwait", DEFAULT_SELECT_TIMEOUT) * 1000

    /** Like mySelectTimeout, but when connection is in debug mode.  */
    private val myDebugSelectTimeout = props.intProperty("conf.comm.httpselectwait.debug", DEFAULT_SELECT_TIMEOUT) * 1000

    /** Time an HTTP session can sit idle before being closed, in
     * milliseconds.  */
    private val mySessionTimeout = props.intProperty("conf.comm.httptimeout", DEFAULT_SESSION_TIMEOUT) * 1000

    /** Like mySessionTimeout, but when connection is in debug mode.  */
    private val myDebugSessionTimeout = props.intProperty("conf.comm.httptimeout.debug", DEFAULT_SESSION_TIMEOUT) * 1000

    /**
     * Add a session to the session table.
     *
     * @param session  The session to add.
     */
    fun addSession(session: HttpSessionConnection) {
        mySessions[session.sessionID] = session
    }

    /**
     * Record the association of a TCP connection with an HTTP session.
     *
     * @param session  The session.
     * @param connection  The connection to associate with the session.
     */
    private fun associateTCPConnection(session: HttpSessionConnection,
                                       connection: Connection) {
        val knownSession = mySessionsByConnection[connection]
        knownSession?.dissociateTCPConnection(connection)
        mySessionsByConnection[connection] = session
        session.associateTCPConnection(connection)
    }

    /**
     * Handle an HTTP GET of a /connect/ URI, causing the creation of a new
     * session.
     *
     * @param connection  The TCP connection upon which the connection request
     * was received.
     * @return true if an HTTP reply was sent.
     */
    private fun doConnect(connection: Connection): Boolean {
        val session = httpSessionConnectionFactory.create(this)
        associateTCPConnection(session, connection)
        handlerFactoryCommGorgel.i?.run { info("$session connect over $connection") }
        val reply = httpFramer.makeConnectReply(session.sessionID)
        connection.sendMsg(reply)
        return true
    }

    /**
     * Handle an HTTP GET of a /disconnect/ URI, causing the explicit
     * termination of an HTTP session by the browser.
     *
     * @param connection  The TCP connection upon which the disconnect request
     * was received.
     * @param uri  HTTP GET URI fields.
     *
     * @return true if an HTTP reply was sent.
     */
    private fun doDisconnect(connection: Connection, uri: SessionUri): Boolean {
        val session = lookupSessionFromUri(connection, uri)
        if (session != null) {
            associateTCPConnection(session, connection)
            session.noteClientActivity()
        }
        val reply: String
        reply = if (session == null) {
            handlerFactoryCommGorgel.error("got disconnect with invalid session ${uri.sessionID}")
            httpFramer.makeSequenceErrorReply("sessionIDError")
        } else {
            httpFramer.makeDisconnectReply()
        }
        connection.sendMsg(reply)
        session?.close()
        return true
    }

    /**
     * Handle an HTTP GET or POST of a bad URI.
     *
     * @param connection  The TCP connection upon which the bad URI
     * request was received.
     * @param uri  The bad URI that was requested.
     *
     * @return true if an HTTP reply was sent.
     */
    private fun doError(connection: Connection, uri: String): Boolean {
        handlerFactoryCommGorgel.i?.run { info("$connection received invalid URI in HTTP request $uri") }
        connection.sendMsg(HttpError(404, "Not Found",
                httpFramer.makeBadURLReply(uri)))
        return true
    }

    /**
     * Handle an HTTP GET of a /select/ URI, requesting the delivery of
     * messages from the server to the client.
     *
     * @param connection  The TCP connection upon which the select request was
     * received.
     * @param uri  HTTP GET URI fields.
     * @param nonPersistent  True if this request was flagged non-persistent.
     *
     * @return true if an HTTP reply was sent.
     */
    private fun doSelect(connection: Connection, uri: SessionUri,
                         nonPersistent: Boolean): Boolean {
        val session = lookupSessionFromUri(connection, uri)
        return if (session != null) {
            associateTCPConnection(session, connection)
            session.selectMessages(connection, uri, nonPersistent)
        } else {
            handlerFactoryCommGorgel.error("got select with invalid session ${uri.sessionID}")
            connection.sendMsg(
                    httpFramer.makeSequenceErrorReply("sessionIDError"))
            true
        }
    }

    /**
     * Handle an HTTP GET or POST of an /xmit/ URI, transmitting messages from
     * the client to the server.
     *
     * @param connection  The TCP connection upon which the message(s)
     * was(were) delivered.
     * @param uri  HTTP GET or POST URI fields.
     * @param message   The body of the message(s) sent from the client.
     *
     * @return true if an HTTP reply was sent.
     */
    private fun doXmit(connection: Connection, uri: SessionUri,
                       message: String): Boolean {
        val session = lookupSessionFromUri(connection, uri)
        if (session != null) {
            associateTCPConnection(session, connection)
            session.receiveMessage(connection, uri, message)
        } else {
            handlerFactoryCommGorgel.error("got xmit with invalid session ${uri.sessionID}")
            connection.sendMsg(
                    httpFramer.makeSequenceErrorReply("sessionIDError"))
        }
        return true
    }

    /**
     * Look up the session associated with some session ID.
     *
     * @param sessionID  The ID number of the session sought.
     *
     * @return the session whose session ID is 'sessionID', or null if there is
     * no such session.
     */
    private fun getSession(sessionID: Long): HttpSessionConnection? = mySessions[sessionID]

    /**
     * Process an HTTP GET request, which (depending on the URI) may be
     * variously a request to connect a new session, to poll for server to
     * client messages for a session, a delivery of client to server messages
     * for a session, or to a request to disconnect a session.
     *
     * @param connection  The TCP connection on which the HTTP request was
     * received.
     * @param uri  The URI that was requested.
     * @param nonPersistent  True if this request was flagged non-persistent.
     */
    fun handleGET(connection: Connection, uri: String, nonPersistent: Boolean) {
        val parsed = SessionUri(uri, myRootUri)
        val replied: Boolean
        replied = if (!parsed.valid) {
            doError(connection, uri)
        } else if (parsed.verb == SessionUri.VERB_CONNECT) {
            doConnect(connection)
        } else if (parsed.verb == SessionUri.VERB_SELECT) {
            doSelect(connection, parsed, nonPersistent)
        } else if (parsed.verb == SessionUri.VERB_DISCONNECT) {
            doDisconnect(connection, parsed)
        } else {
            doError(connection, uri)
        }
        if (replied && nonPersistent) {
            connection.close()
        }
    }

    /**
     * Process an HTTP OPTIONS request, used in the brain damaged, useless, but
     * seemingly inescapable request preflight handshake required by the CORS
     * standard for cross site request handling.
     *
     * @param connection  The TCP connection on which the HTTP request was
     * received.
     * @param request  The HTTP request itself, from which we will extract
     * header information.
     */
    fun handleOPTIONS(connection: Connection, request: HttpRequest) {
        handlerFactoryCommGorgel.i?.run { info("OPTIONS request over $connection") }
        val reply = HttpOptionsReply(request)
        connection.sendMsg(reply)
    }

    /**
     * Process an HTTP POST request, delivering messages for a session.
     *
     * @param connection   The TCP connection on which the HTTP request was
     * received.
     * @param uri  The URI that was posted.
     * @param nonPersistent  True if this request was flagged non-persistent.
     * @param message  The message body.
     */
    fun handlePOST(connection: Connection, uri: String, nonPersistent: Boolean, message: String?) {
        val parsed = SessionUri(uri, myRootUri)
        val replied: Boolean
        replied = if (!parsed.valid) {
            doError(connection, uri)
        } else if (parsed.verb == SessionUri.VERB_SELECT) {
            doSelect(connection, parsed, nonPersistent)
        } else if (parsed.verb == SessionUri.VERB_XMIT_POST) {
            doXmit(connection, parsed, message!!)
        } else if (parsed.verb == SessionUri.VERB_CONNECT) {
            doConnect(connection)
        } else if (parsed.verb == SessionUri.VERB_DISCONNECT) {
            doDisconnect(connection, parsed)
        } else {
            doError(connection, uri)
        }
        if (replied && nonPersistent) {
            connection.close()
        }
    }

    /**
     * Determine the HTTP session object associated with a requested URI.
     *
     * @param connection  The connection that referenced the URI.
     * @param uri  The URI uri describing the session of interest.
     *
     * @return the HTTP session corresponding to the session ID in 'uri', or
     * null if there was no such session.
     */
    private fun lookupSessionFromUri(connection: Connection,
                                     uri: SessionUri): HttpSessionConnection? {
        val session = getSession(uri.sessionID)
        if (session != null) {
            return session
        }
        handlerFactoryCommGorgel.i?.run { info("$connection received invalid session ID ${uri.sessionID}") }
        return null
    }

    /**
     * Provide a message handler for a new (HTTP over TCP) connection.
     *
     * @param connection  The TCP connection object that was just created.
     */
    override fun provideMessageHandler(connection: Connection?): MessageHandler = HttpMessageHandler(connection!!, this, sessionTimeout(false), timer, handlerCommGorgel)

    /**
     * Remove a session from the session table.
     *
     * @param session  The session to remove.
     */
    fun removeSession(session: HttpSessionConnection) {
        mySessions.remove(session.sessionID)
    }

    /**
     * Get the HTTP select timeout interval: the time an HTTP request for a
     * select URL can remain open with no message traffic before the server
     * must respond.
     *
     * @param debug  If true, return the debug-mode timeout; if false, return
     * the normal use timeout.
     *
     * @return the select timeout interval, in milliseconds.
     */
    fun selectTimeout(debug: Boolean) =
            if (debug) myDebugSelectTimeout else mySelectTimeout

    /**
     * Get the HTTP session timeout interval: the time an HTTP session can be
     * idle before the server closes it.
     *
     * @param debug  If true, return the debug-mode timeout; if false, return
     * the normal use timeout.
     *
     * @return the session timeout interval, in milliseconds.
     */
    fun sessionTimeout(debug: Boolean) =
            if (debug) myDebugSessionTimeout else mySessionTimeout

    /**
     * Receive notification that an underlying TCP connection has died.
     *
     * @param connection  The TCP connection that died.
     * @param reason  The reason why.
     */
    fun tcpConnectionDied(connection: Connection, reason: Throwable) {
        val session = mySessionsByConnection.remove(connection)
        if (session != null) {
            session.dissociateTCPConnection(connection)
            handlerFactoryCommGorgel.i?.run { info("$connection lost under $session: $reason") }
        } else {
            handlerFactoryCommGorgel.i?.run { info("$connection lost under no known HTTP session: $reason") }
        }
    }

    companion object {
        /** Default select timeout if none is explicitly given, in seconds.  */
        private const val DEFAULT_SELECT_TIMEOUT = 60

        /** Default session timeout if none is explicitly given, in seconds.  */
        private const val DEFAULT_SESSION_TIMEOUT = 15
    }
}
