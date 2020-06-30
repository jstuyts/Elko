package org.elkoserver.foundation.net.tcp.server

import org.elkoserver.foundation.byteioframer.ByteIOFramerFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.net.SelectThread
import java.io.IOException

class TcpServerFactory(
        private val mySelectThread: SelectThread) {

    /**
     * Begin listening for incoming TCP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide message
     * handlers for connections made to this port.
     * @param secure  If true, use SSL.
     * @param framerFactory  Byte I/O framer factory for the new connection.
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenTCP(listenAddress: String,
                  handlerFactory: MessageHandlerFactory,
                  secure: Boolean, framerFactory: ByteIOFramerFactory): NetAddr {
        val listener = mySelectThread.listen(listenAddress, handlerFactory, framerFactory, secure)
        return listener.listenAddress()
    }
}