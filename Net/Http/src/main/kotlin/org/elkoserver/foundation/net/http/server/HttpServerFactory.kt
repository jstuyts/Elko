package org.elkoserver.foundation.net.http.server

import org.elkoserver.foundation.byteioframer.http.HTTPRequestByteIOFramerFactoryFactory
import org.elkoserver.foundation.net.LoadMonitor
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.net.tcp.server.TcpServerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException
import java.time.Clock

class HttpServerFactory(
        private val props: ElkoProperties,
        private val loadMonitor: LoadMonitor,
        private val runner: Runner,
        private val timer: Timer,
        private val clock: Clock,
        private val httpSessionConnectionCommGorgel: Gorgel,
        private val traceFactory: TraceFactory,
        private val sessionIdGenerator: IdGenerator,
        private val connectionIdGenerator: IdGenerator,
        private val tcpServerFactory: TcpServerFactory,
        private val httpRequestByteIOFramerFactoryFactory: HTTPRequestByteIOFramerFactoryFactory) {

    /**
     * Begin listening for incoming HTTP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     * handlers for messages passed inside HTTP requests on connections made
     * to this port.
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
                   trace: Trace,
                   secure: Boolean, rootURI: String, httpFramer: HTTPFramer): NetAddr {
        val outerHandlerFactory = HTTPMessageHandlerFactory(
                innerHandlerFactory, rootURI, httpFramer, runner, loadMonitor, props, timer, clock, httpSessionConnectionCommGorgel, traceFactory, sessionIdGenerator, connectionIdGenerator)
        val framerFactory = httpRequestByteIOFramerFactoryFactory.create()
        return tcpServerFactory.listenTCP(listenAddress, outerHandlerFactory, secure, framerFactory, trace)
    }
}
