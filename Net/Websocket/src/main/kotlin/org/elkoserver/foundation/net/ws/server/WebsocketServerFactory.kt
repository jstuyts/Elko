package org.elkoserver.foundation.net.ws.server

import org.elkoserver.foundation.byteioframer.websocket.WebsocketByteIOFramerFactoryFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.net.tcp.server.TcpServerFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

class WebsocketServerFactory(
        private val tcpServerFactory: TcpServerFactory,
        private val websocketByteIOFramerFactoryFactory: WebsocketByteIOFramerFactoryFactory) {

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
    fun listenWebsocket(
            listenAddress: String,
            innerHandlerFactory: MessageHandlerFactory,
            secure: Boolean,
            socketURI: String,
            gorgel: Gorgel): NetAddr {
        var actualSocketURI = socketURI
        if (!actualSocketURI.startsWith("/")) {
            actualSocketURI = "/$actualSocketURI"
        }
        val outerHandlerFactory = WebsocketMessageHandlerFactory(innerHandlerFactory, actualSocketURI, gorgel)
        val framerFactory = websocketByteIOFramerFactoryFactory.create(listenAddress, actualSocketURI)
        return tcpServerFactory.listenTCP(listenAddress, outerHandlerFactory, secure, framerFactory)
    }
}
