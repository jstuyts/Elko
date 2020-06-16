package org.elkoserver.foundation.net

import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.TraceFactory
import java.time.Clock

/**
 * Base class providing common internals implementation for various types of
 * Connection objects.
 *
 * @param mgr  Network manager for this server.
 */
abstract class ConnectionBase protected constructor(mgr: NetworkManager, protected var clock: Clock, protected val traceFactory: TraceFactory, idGenerator: IdGenerator) : Connection {
    /** Number identifying this connection in log messages.  */
    private val myID: Int

    /** Handler for incoming messages.  */
    private var myMessageHandler: MessageHandler? = null

    /** The run queue in which messages will be handled.  */ /* protected */
    private val myRunner = mgr.runner

    /** System load tracker.  */
    private val myLoadMonitor: LoadMonitor?

    /**
     * Cope with loss of a connection.
     *
     * @param reason  Throwable describing why the connection died.
     */
    protected fun connectionDied(reason: Throwable) {
        if (myMessageHandler != null) {
            if (traceFactory.comm.debug) {
                traceFactory.comm.debugm("$this calls connectionDied in $myMessageHandler")
            }
            myMessageHandler!!.connectionDied(this, reason)
        } else {
            traceFactory.comm.debugm(this.toString() +
                    " ignores connection death while message handler is null")
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
    private inner class MessageHandlerThunk internal constructor(private val myMessage: Any) : Runnable {
        private var myOnQueueTime: Long = 0
        override fun run() {
            if (myMessageHandler != null) {
                if (traceFactory.comm.verbose) {
                    traceFactory.comm.verbosem("$this calls processMessage in $myMessageHandler")
                }
                myMessageHandler!!.processMessage(this@ConnectionBase, myMessage)
            } else {
                traceFactory.comm.verbosem("$this ignores message received while message handler is null")
            }
            myLoadMonitor?.addTime(
                    clock.millis() - myOnQueueTime)
        }

        init {
            if (myLoadMonitor != null) {
                myOnQueueTime = clock.millis()
            }
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

    private inner class HandlerFactoryThunk internal constructor(private val myHandlerFactory: MessageHandlerFactory) : Runnable {
        override fun run() {
            myMessageHandler = myHandlerFactory.provideMessageHandler(this@ConnectionBase)
            if (myMessageHandler == null) {
                if (traceFactory.comm.debug) {
                    traceFactory.comm.debugm("$this connection setup failed")
                }
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
        val theCloseMarker = Any()
    }

    init {
        myLoadMonitor = mgr.loadMonitor
        myID = idGenerator.generate().toInt()
    }
}
