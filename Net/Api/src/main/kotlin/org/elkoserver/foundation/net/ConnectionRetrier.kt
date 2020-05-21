package org.elkoserver.foundation.net

import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory

/**
 * Worker object to manage an ongoing attempt to establish an outbound TCP
 * connection, so that failed connection attempts can be retried automatically.
 *
 * @param myHost  Description of host to connect to.
 * @param myLabel  String describing remote connection endpoint, for
 * diagnostic output
 * @param networkManager  Network manager to actually make the connection.
 * @param actualFactory  Application-provided message handler factory for
 * use once connection is established.
 * @param appTrace  Application trace object for logging.
 */
class ConnectionRetrier(
        private val myHost: HostDesc,
        private val myLabel: String,
        networkManager: NetworkManager,
        actualFactory: MessageHandlerFactory,
        timer: Timer,
        appTrace: Trace, traceFactory: TraceFactory?) {

    /** Low-level I/O framer factory for the new connection.  */
    private val myFramerFactory: JSONByteIOFramerFactory

    /** Flag to stop retries.  */
    private var myKeepTryingFlag = true

    /** Message handler factory to use once connection is established.  */
    private val myActualFactory: MessageHandlerFactory

    /** Message handler factory to use when connection attempt is pending.  */
    private val myRetryHandlerFactory: MessageHandlerFactory

    /** Network manager, for making the outbound connections.  */
    private val myNetworkManager: NetworkManager

    /** Timeout handler to retry failed connection attempts after a while.  */
    private val myRetryTimeout: TimeoutNoticer

    /** Trace object for logging activity associated with the new connection  */
    private val myTrace = appTrace.subTrace(myLabel)

    /**
     * Attempt to make the connection.
     */
    private fun doConnect(outerHandlerFactory: MessageHandlerFactory) {
        myNetworkManager.connectTCP(myHost.hostPort!!, outerHandlerFactory, myFramerFactory, myTrace)
    }

    /**
     * Stop retrying this connection.
     */
    fun giveUp() {
        myKeepTryingFlag = false
    }

    init {
        myFramerFactory = JSONByteIOFramerFactory(myTrace, traceFactory!!)
        myNetworkManager = networkManager
        myActualFactory = actualFactory
        myTrace.eventi("connecting to $myLabel at ${myHost.hostPort}")
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
            myTrace.eventi("retrying connection to $myLabel at ${myHost.hostPort}")
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
