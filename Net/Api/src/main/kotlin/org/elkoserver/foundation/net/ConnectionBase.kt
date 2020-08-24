package org.elkoserver.foundation.net

import org.elkoserver.foundation.run.Runner
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.slf4j.Gorgel
import java.time.Clock

/**
 * Base class providing common internals implementation for various types of
 * Connection objects.
 */
abstract class ConnectionBase protected constructor(
        private val myRunner: Runner,
        private val myLoadMonitor: LoadMonitor,
        protected var clock: Clock,
        protected val commGorgel: Gorgel,
        idGenerator: IdGenerator) : Connection {
    /** Number identifying this connection in log messages.  */
    private val myID = idGenerator.generate().toInt()

    /** Handler for incoming messages.  */
    private var myMessageHandler: MessageHandler? = null

    /**
     * Cope with loss of a connection.
     *
     * @param reason  Throwable describing why the connection died.
     */
    protected fun connectionDied(reason: Throwable) {
        val currentMessageHandler = myMessageHandler
        if (currentMessageHandler != null) {
            commGorgel.d?.run { debug("${this@ConnectionBase} calls connectionDied in $currentMessageHandler") }
            currentMessageHandler.connectionDied(this, reason)
        } else {
            commGorgel.d?.run { debug("${this@ConnectionBase} ignores connection death while message handler is null") }
        }
    }

    /**
     * Identify this connection for logging purposes.
     *
     * @return this connection's ID number.
     */
    override fun id(): Int = myID

    /**
     * Enqueue a received message for processing.
     *
     * @param message  The received message.
     */
    protected fun enqueueReceivedMessage(message: Any) {
        if (myMessageHandler is MessageAcquirer) {
            (myMessageHandler as MessageAcquirer).acquireMessage(message)
        }
        myRunner.enqueue(MessageHandlerThunk(message))
    }

    /**
     * Process a received message from the run queue.
     */
    private inner class MessageHandlerThunk(private val myMessage: Any) : Runnable {
        private val myOnQueueTime = clock.millis()

        override fun run() {
            val currentMessageHandler = myMessageHandler
            if (currentMessageHandler != null) {
                commGorgel.i?.run { info("${this@ConnectionBase} calls processMessage in $currentMessageHandler") }
                currentMessageHandler.processMessage(this@ConnectionBase, myMessage)
            } else {
                commGorgel.i?.run { info("${this@ConnectionBase} ignores message received while message handler is null") }
            }
            myLoadMonitor.addTime(clock.millis() - myOnQueueTime)
        }
    }

    /**
     * Enqueue a task to invoke a message handler factory to produce a message
     * handler for this connection.
     *
     * @param handlerFactory  Provider of a message handler to process messages
     * received on this connection.
     */
    protected fun enqueueHandlerFactory(handlerFactory: MessageHandlerFactory) {
        myRunner.enqueue(HandlerFactoryThunk(handlerFactory))
    }

    private inner class HandlerFactoryThunk(private val myHandlerFactory: MessageHandlerFactory) : Runnable {
        override fun run() {
            myMessageHandler = myHandlerFactory.provideMessageHandler(this@ConnectionBase)
            if (myMessageHandler == null) {
                commGorgel.d?.run { debug("${this@ConnectionBase} connection setup failed") }
                close()
            }
        }

    }

    /**
     * Turn on or off debug features on this connection.
     *
     * This is an empty implementation so that subclasses only need to
     * implement this feature if they wish to.
     *
     * @param mode  If true, turn debug mode on; if false, turn it off.
     */
    override fun setDebugMode(mode: Boolean) {
        /* Children may implement this method at their option. */
    }

    companion object {
        /** Token to put on send queue to signal close of connection.  */
        val theCloseMarker: Any = Any()
    }

}
