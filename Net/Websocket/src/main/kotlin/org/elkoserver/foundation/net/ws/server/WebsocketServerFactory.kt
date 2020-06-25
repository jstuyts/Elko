package org.elkoserver.foundation.net.ws.server

import org.elkoserver.foundation.byteioframer.websocket.WebsocketByteIOFramerFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.net.tcp.server.TcpServerFactory
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

class WebsocketServerFactory(
        private val inputGorgel: Gorgel,
        private val jsonByteIOFramerGorgel: Gorgel,
        private val websocketFramerGorgel: Gorgel,
        private val mustSendDebugReplies: Boolean,
        private val tcpServerFactory: TcpServerFactory) {

    /**
     * Begin listening for incoming WebSocket connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     * handlers for messages passed inside WebSocket frames on connections
     * made to this port.
     * @param secure  If true, use SSL.
     *
     * @param socketURI  The WebSocket URI that browsers connect to
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenWebsocket(listenAddress: String,
                        innerHandlerFactory: MessageHandlerFactory,
                        secure: Boolean, socketURI: String, trace: Trace): NetAddr {
        var actualSocketURI = socketURI
        if (!actualSocketURI.startsWith("/")) {
            actualSocketURI = "/$actualSocketURI"
        }
        val outerHandlerFactory = WebsocketMessageHandlerFactory(innerHandlerFactory, actualSocketURI, trace)
        val framerFactory = WebsocketByteIOFramerFactory(jsonByteIOFramerGorgel, websocketFramerGorgel, listenAddress, actualSocketURI, inputGorgel, mustSendDebugReplies)
        return tcpServerFactory.listenTCP(listenAddress, outerHandlerFactory, secure, framerFactory, trace)
    }
}
