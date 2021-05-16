package org.elkoserver.foundation.net.connectionretrier

import org.elkoserver.foundation.byteioframer.json.JsonByteIoFramerFactory
import org.elkoserver.foundation.byteioframer.json.JsonByteIoFramerFactoryFactory
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.tcp.client.TcpClientFactory
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Worker object to manage an ongoing attempt to establish an outbound TCP
 * connection, so that failed connection attempts can be retried automatically.
 *
 * @param myHost  Description of host to connect to.
 * @param myLabel  String describing remote connection endpoint, for
 * diagnostic output
 * @param myActualFactory  Application-provided message handler factory for
 * use once connection is established.
 */
class ConnectionRetrier(
    private val myHost: HostDesc,
    private val myLabel: String,
    private val tcpClientFactory: TcpClientFactory,
    private val myActualFactory: MessageHandlerFactory,
    private val timer: Timer,
    private val gorgel: Gorgel,
    jsonByteIoFramerFactoryFactory: JsonByteIoFramerFactoryFactory
) {

    /** Low-level I/O framer factory for the new connection.  */
    private val myFramerFactory: JsonByteIoFramerFactory = jsonByteIoFramerFactoryFactory.create(myLabel)

    /** Flag to stop retries.  */
    private var myKeepTryingFlag = true

    /** Message handler factory to use when connection attempt is pending.  */
    private val myRetryHandlerFactory =
        object : MessageHandlerFactory {
            override fun provideMessageHandler(connection: Connection) =
                myActualFactory.provideMessageHandler(connection)

            override fun handleConnectionFailure() {
                if (myKeepTryingFlag) {
                    timer.after(myHost.retryInterval * 1000.toLong(), myRetryTimeout)
                }
            }
        }

    /** Timeout handler to retry failed connection attempts after a while.  */
    private val myRetryTimeout = TimeoutNoticer { handleRetryTimeout() }

    /**
     * Attempt to make the connection.
     */
    private fun doConnect(outerHandlerFactory: MessageHandlerFactory) {
        tcpClientFactory.connectTCP(myHost.hostPort!!, outerHandlerFactory, myFramerFactory)
    }

    /**
     * Stop retrying this connection.
     */
    fun giveUp() {
        myKeepTryingFlag = false
    }

    init {
        gorgel.i?.run { info("connecting to $myLabel at ${myHost.hostPort}") }
        doConnect(myRetryHandlerFactory)
    }

    private fun handleRetryTimeout() {
        if (myKeepTryingFlag) {
            gorgel.i?.run { info("retrying connection to $myLabel at ${myHost.hostPort}") }
            doConnect(myRetryHandlerFactory)
        }
    }
}
