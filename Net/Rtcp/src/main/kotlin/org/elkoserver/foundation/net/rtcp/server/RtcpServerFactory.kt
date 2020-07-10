package org.elkoserver.foundation.net.rtcp.server

import org.elkoserver.foundation.byteioframer.rtcp.RtcpRequestByteIoFramerFactoryFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.net.tcp.server.TcpServerFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

class RtcpServerFactory(
        private val rtcpMessageHandlerFactoryFactory: RtcpMessageHandlerFactoryFactory,
        private val tcpServerFactory: TcpServerFactory,
        private val rtcpRequestByteIoFramerFactoryFactory: RtcpRequestByteIoFramerFactoryFactory) {

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
                   rtcpMessageHandlerFactoryGorgel: Gorgel): NetAddr {
        val outerHandlerFactory = rtcpMessageHandlerFactoryFactory.create(innerHandlerFactory, rtcpMessageHandlerFactoryGorgel)
        val framerFactory = rtcpRequestByteIoFramerFactoryFactory.create()
        return tcpServerFactory.listenTCP(listenAddress, outerHandlerFactory, secure, framerFactory)
    }
}
