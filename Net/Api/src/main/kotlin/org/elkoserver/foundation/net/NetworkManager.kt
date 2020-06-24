package org.elkoserver.foundation.net

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException
import java.time.Clock

/**
 * Manage network connections between this server and other entities.
 *
 * @param props  Boot properties for this server.
 * @param loadMonitor  Load monitor for tracking system load.
 * @param runner  The Runner managing this server's run queue.
 */
class NetworkManager(
        private val props: ElkoProperties,
        val loadMonitor: LoadMonitor,
        val runner: Runner,
        private val timer: Timer,
        private val clock: Clock,
        private val httpSessionConnectionCommGorgel: Gorgel,
        private val rtcpSessionConnectionCommGorgel: Gorgel,
        private val traceFactory: TraceFactory,
        private val inputGorgel: Gorgel,
        private val sessionIdGenerator: IdGenerator,
        private val connectionIdGenerator: IdGenerator,
        private val mustSendDebugReplies: Boolean,
        private val mySelectThread: SelectThread) {

    /**
     * Make a TCP connection to another host given a host:port address.
     *
     * @param hostPort  The host name (or IP address) and port to connect to,
     * separated by a colon.  For example, "bithlo.example.com:8002".
     * @param handlerFactory  Message handler factory to provide the handler
     * for the connection that results from this operation.
     * @param framerFactory  Byte I/O framer factory for the new connection.
     * @param trace  Trace object to use for activity on the new connection.
     */
    fun connectTCP(hostPort: String,
                   handlerFactory: MessageHandlerFactory,
                   framerFactory: ByteIOFramerFactory, trace: Trace) {
        mySelectThread.connect(handlerFactory, framerFactory, hostPort, trace)
    }

    /**
     * Begin listening for incoming HTTP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     * handlers for messages passed inside HTTP requests on connections made
     * to this port.
     * @param tcpConnectionTrace  Trace object to use for activity on this connection.
     *
     * @param secure  If true, use SSL.
     * @param rootURI  The root URI that GETs and POSTs must reference.
     * @param httpFramer  HTTP framer to interpret HTTP POSTs and format HTTP
     * replies.
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenHTTP(listenAddress: String,
                   innerHandlerFactory: MessageHandlerFactory,
                   listenerGorgel: Gorgel,
                   tcpConnectionTrace: Trace, secure: Boolean, rootURI: String, httpFramer: HTTPFramer): NetAddr {
        val outerHandlerFactory: MessageHandlerFactory = HTTPMessageHandlerFactory(
                innerHandlerFactory, rootURI, httpFramer, runner, loadMonitor, props, timer, clock, httpSessionConnectionCommGorgel, traceFactory, sessionIdGenerator, connectionIdGenerator)
        val framerFactory: ByteIOFramerFactory = HTTPRequestByteIOFramerFactory(traceFactory, inputGorgel)
        return listenTCP(listenAddress, outerHandlerFactory, listenerGorgel, tcpConnectionTrace, secure, framerFactory)
    }

    /**
     * Begin listening for incoming RTCP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     * handlers for messages passed inside RTCP requests on connections made
     * to this port.
     * @param tcpConnectionTrace   Trace object for logging message traffic
     * @param secure  If true, use SSL.
     *
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenRTCP(listenAddress: String,
                   innerHandlerFactory: MessageHandlerFactory,
                   listenerGorgel: Gorgel,
                   tcpConnectionTrace: Trace,
                   tcpConnectionGorgel: Gorgel,
                   secure: Boolean): NetAddr {
        val outerHandlerFactory: MessageHandlerFactory = RTCPMessageHandlerFactory(innerHandlerFactory, rtcpSessionConnectionCommGorgel, tcpConnectionTrace, runner, loadMonitor, props, timer, clock, traceFactory, sessionIdGenerator, connectionIdGenerator)
        val framerFactory: ByteIOFramerFactory = RTCPRequestByteIOFramerFactory(tcpConnectionGorgel, inputGorgel, mustSendDebugReplies)
        return listenTCP(listenAddress, outerHandlerFactory, listenerGorgel, tcpConnectionTrace, secure, framerFactory)
    }

    /**
     * Begin listening for incoming WebSocket connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     * handlers for messages passed inside WebSocket frames on connections
     * made to this port.
     * @param tcpConnectionTrace   Trace object for logging message traffic
     * @param secure  If true, use SSL.
     *
     * @param socketURI  The WebSocket URI that browsers connect to
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenWebSocket(listenAddress: String,
                        innerHandlerFactory: MessageHandlerFactory,
                        listenerGorgel: Gorgel,
                        jsonByteIOFramerGorgel: Gorgel,
                        websocketFramerGorgel: Gorgel,
                        tcpConnectionTrace: Trace, secure: Boolean, socketURI: String): NetAddr {
        var actualSocketURI = socketURI
        if (!actualSocketURI.startsWith("/")) {
            actualSocketURI = "/$actualSocketURI"
        }
        val outerHandlerFactory: MessageHandlerFactory = WebSocketMessageHandlerFactory(innerHandlerFactory, actualSocketURI,
                tcpConnectionTrace)
        val framerFactory: ByteIOFramerFactory = WebSocketByteIOFramerFactory(jsonByteIOFramerGorgel, websocketFramerGorgel, listenAddress, actualSocketURI, inputGorgel, mustSendDebugReplies)
        return listenTCP(listenAddress, outerHandlerFactory, listenerGorgel, tcpConnectionTrace, secure, framerFactory)
    }

    /**
     * Begin listening for incoming TCP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide message
     * handlers for connections made to this port.
     * @param listenerGorgel  Trace object for logging activity associated with this
     * port &amp; its connections
     *
     * @param secure  If true, use SSL.
     * @param framerFactory  Byte I/O framer factory for the new connection.
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenTCP(listenAddress: String,
                  handlerFactory: MessageHandlerFactory,
                  listenerGorgel: Gorgel, tcpConnectionTrace: Trace, secure: Boolean, framerFactory: ByteIOFramerFactory): NetAddr {
        val listener = mySelectThread.listen(listenAddress, handlerFactory,
                framerFactory, secure, listenerGorgel, tcpConnectionTrace)
        return listener.listenAddress()
    }
}
