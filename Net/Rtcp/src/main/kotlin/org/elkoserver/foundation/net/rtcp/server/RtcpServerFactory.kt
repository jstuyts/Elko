package org.elkoserver.foundation.net.rtcp.server

import org.elkoserver.foundation.net.LoadMonitor
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.net.RTCPRequestByteIOFramerFactory
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

class RtcpServerFactory(
        private val props: ElkoProperties,
        private val loadMonitor: LoadMonitor,
        private val runner: Runner,
        private val timer: Timer,
        private val clock: Clock,
        private val rtcpSessionConnectionCommGorgel: Gorgel,
        private val traceFactory: TraceFactory,
        private val inputGorgel: Gorgel,
        private val tcpConnectionGorgel: Gorgel,
        private val sessionIdGenerator: IdGenerator,
        private val connectionIdGenerator: IdGenerator,
        private val mustSendDebugReplies: Boolean,
        private val tcpServerFactory: TcpServerFactory) {

    /**
     * Begin listening for incoming RTCP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     * handlers for messages passed inside RTCP requests on connections made
     * to this port.
     * @param secure  If true, use SSL.
     *
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenRTCP(listenAddress: String,
                   innerHandlerFactory: MessageHandlerFactory,
                   secure: Boolean,
                   trace: Trace): NetAddr {
        val outerHandlerFactory = RTCPMessageHandlerFactory(innerHandlerFactory, rtcpSessionConnectionCommGorgel, trace, runner, loadMonitor, props, timer, clock, traceFactory, sessionIdGenerator, connectionIdGenerator)
        val framerFactory = RTCPRequestByteIOFramerFactory(tcpConnectionGorgel, inputGorgel, mustSendDebugReplies)
        return tcpServerFactory.listenTCP(listenAddress, outerHandlerFactory, secure, framerFactory, trace)
    }
}