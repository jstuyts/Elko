package org.elkoserver.foundation.net.ws.server

import org.elkoserver.foundation.byteioframer.websocket.WebsocketByteIoFramerFactoryFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.net.tcp.server.TcpServerFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

class WebsocketServerFactory(
        private val tcpServerFactory: TcpServerFactory,
        private val websocketByteIoFramerFactoryFactory: WebsocketByteIoFramerFactoryFactory) {

    /**
     * Begin listening for incoming WebSocket connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     * handlers for messages passed inside WebSocket frames on connections
     * made to this port.
     * @param secure  If true, use SSL.
     *
     * @param socketUri  The WebSocket URI that browsers connect to
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenWebsocket(
            listenAddress: String,
            innerHandlerFactory: MessageHandlerFactory,
            secure: Boolean,
            socketUri: String,
            gorgel: Gorgel): NetAddr {
        var actualSocketUri = socketUri
        if (!actualSocketUri.startsWith("/")) {
            actualSocketUri = "/$actualSocketUri"
        }
        val outerHandlerFactory = WebsocketMessageHandlerFactory(innerHandlerFactory, actualSocketUri, gorgel)
        val framerFactory = websocketByteIoFramerFactoryFactory.create(listenAddress, actualSocketUri)
        return tcpServerFactory.listenTCP(listenAddress, outerHandlerFactory, secure, framerFactory)
    }
}
