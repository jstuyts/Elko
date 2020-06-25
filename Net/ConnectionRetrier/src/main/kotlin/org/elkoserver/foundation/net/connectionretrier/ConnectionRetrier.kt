package org.elkoserver.foundation.net.connectionretrier

import org.elkoserver.foundation.byteioframer.json.JSONByteIOFramerFactory
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.tcp.client.TcpClientFactory
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.Trace
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
 * @param appTrace  Application trace object for logging.
 */
class ConnectionRetrier(
        private val myHost: HostDesc,
        private val myLabel: String,
        private val tcpClientFactory: TcpClientFactory,
        private val myActualFactory: MessageHandlerFactory,
        timer: Timer,
        private val gorgel: Gorgel,
        jsonByteIOFramerGorgel: Gorgel,
        appTrace: Trace,
        inputGorgel: Gorgel,
        mustSendDebugReplies: Boolean) {

    /** Low-level I/O framer factory for the new connection.  */
    private val myFramerFactory: JSONByteIOFramerFactory = JSONByteIOFramerFactory(jsonByteIOFramerGorgel, inputGorgel, mustSendDebugReplies)

    /** Flag to stop retries.  */
    private var myKeepTryingFlag = true

    /** Message handler factory to use when connection attempt is pending.  */
    private val myRetryHandlerFactory: MessageHandlerFactory

    /** Timeout handler to retry failed connection attempts after a while.  */
    private val myRetryTimeout: TimeoutNoticer

    /** Trace object for logging activity associated with the new connection  */
    private val myTrace = appTrace.subTrace(myLabel)

    /**
     * Attempt to make the connection.
     */
    private fun doConnect(outerHandlerFactory: MessageHandlerFactory) {
        tcpClientFactory.connectTCP(myHost.hostPort!!, outerHandlerFactory, myFramerFactory, myTrace)
    }

    /**
     * Stop retrying this connection.
     */
    fun giveUp() {
        myKeepTryingFlag = false
    }

    init {
        gorgel.i?.run { info("connecting to $myLabel at ${myHost.hostPort}") }
        myRetryTimeout = object : TimeoutNoticer {
            override fun noticeTimeout() {
                handleRetryTimeout()
            }
        }
        myRetryHandlerFactory = object : MessageHandlerFactory {
            override fun provideMessageHandler(connection: Connection?) = createRetryHandler(connection, timer)
        }
        doConnect(myRetryHandlerFactory)
    }

    private fun handleRetryTimeout() {
        if (myKeepTryingFlag) {
            gorgel.i?.run { info("retrying connection to $myLabel at ${myHost.hostPort}") }
            doConnect(myRetryHandlerFactory)
        }
    }

    private fun createRetryHandler(connection: Connection?, timer: Timer): MessageHandler? {
        return if (connection == null) {
            if (myKeepTryingFlag) {
                timer.after(myHost.retryInterval * 1000.toLong(), myRetryTimeout)
            }
            null
        } else {
            myActualFactory.provideMessageHandler(connection)
        }
    }
}
