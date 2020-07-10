package org.elkoserver.foundation.net.tcp.client

import org.elkoserver.foundation.byteioframer.ByteIoFramerFactory
import org.elkoserver.foundation.net.LoadMonitor
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.SelectThread
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner

/**
 * Manage network connections between this server and other entities.
 *
 * @param props  Boot properties for this server.
 * @param loadMonitor  Load monitor for tracking system load.
 * @param runner  The Runner managing this server's run queue.
 */
class TcpClientFactory(
        private val props: ElkoProperties,
        private val loadMonitor: LoadMonitor,
        private val runner: Runner,
        private val mySelectThread: SelectThread) {

    /**
     * Make a TCP connection to another host given a host:port address.
     *
     * @param hostPort  The host name (or IP address) and port to connect to,
     * separated by a colon.  For example, "bithlo.example.com:8002".
     * @param handlerFactory  Message handler factory to provide the handler
     * for the connection that results from this operation.
     * @param framerFactory  Byte I/O framer factory for the new connection.
     */
    fun connectTCP(hostPort: String,
                   handlerFactory: MessageHandlerFactory,
                   framerFactory: ByteIoFramerFactory) {
        mySelectThread.connect(handlerFactory, framerFactory, hostPort)
    }
}
