package org.elkoserver.foundation.net.http.server

import org.elkoserver.foundation.byteioframer.http.HttpRequestByteIoFramerFactoryFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.net.tcp.server.TcpServerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

class HttpServerFactory(
        private val props: ElkoProperties,
        private val timer: Timer,
        private val handlerCommGorgel: Gorgel,
        private val handlerFactoryCommGorgel: Gorgel,
        private val tcpServerFactory: TcpServerFactory,
        private val httpRequestByteIoFramerFactoryFactory: HttpRequestByteIoFramerFactoryFactory,
        private val httpSessionConnectionFactory: HttpSessionConnectionFactory) {

    /**
     * Begin listening for incoming HTTP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     * handlers for messages passed inside HTTP requests on connections made
     * to this port.
     *
     * @param secure  If true, use SSL.
     * @param rootUri  The root URI that GETs and POSTs must reference.
     * @param httpFramer  HTTP framer to interpret HTTP POSTs and format HTTP
     * replies.
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenHTTP(listenAddress: String,
                   innerHandlerFactory: MessageHandlerFactory,
                   secure: Boolean,
                   rootUri: String, httpFramer: HttpFramer): NetAddr {
        val outerHandlerFactory = HttpMessageHandlerFactory(
                innerHandlerFactory, rootUri, httpFramer, props, timer, handlerCommGorgel, handlerFactoryCommGorgel, httpSessionConnectionFactory)
        val framerFactory = httpRequestByteIoFramerFactoryFactory.create()
        return tcpServerFactory.listenTCP(listenAddress, outerHandlerFactory, secure, framerFactory)
    }
}
