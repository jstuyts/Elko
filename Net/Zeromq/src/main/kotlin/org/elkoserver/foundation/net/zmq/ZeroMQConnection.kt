package org.elkoserver.foundation.net.zmq

import org.elkoserver.foundation.net.ByteIOFramer
import org.elkoserver.foundation.net.ByteIOFramerFactory
import org.elkoserver.foundation.net.ConnectionBase
import org.elkoserver.foundation.net.ConnectionCloseException
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.MessageReceiver
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.run.Queue
import org.elkoserver.foundation.run.Runner
import org.elkoserver.util.trace.TraceFactory
import org.zeromq.ZMQ
import java.io.IOException
import java.time.Clock

/**
 * An implementation of [org.elkoserver.foundation.net.Connection] that
 * manages a message connection over a ZeroMQ socket.
 */
class ZeroMQConnection internal constructor(handlerFactory: MessageHandlerFactory,
                                            framerFactory: ByteIOFramerFactory,
                                            private val mySocket: ZMQ.Socket,
                                            private val amSendMode: Boolean,
                                            private val myThread: ZeroMQThread,
                                            private val myMgr: NetworkManager,
                                            remoteAddr: String, clock: Clock, traceFactory: TraceFactory, private var amOpen: Boolean = true) : ConnectionBase(myMgr, clock, traceFactory), MessageReceiver, Thunk {
    /** Queue of unencoded outbound messages.  */
    private val myOutputQueue = Queue<Any>()

    /** Framer to perform low-level message conversion.  */
    private val myFramer: ByteIOFramer

    /** Monitor lock for syncing with the ZMQ thread.  */
    private val myWakeupLock = Any()

    /** Flag to trigger ZMQ thread to look for write opportunities.  */
    private var amNeedingToWakeupThread = false

    /**
     * Shut down the connection.  Any queued messages will be sent.
     */
    override fun close() {
        if (traceFactory.comm.debug) {
            traceFactory.comm.debugm("$this close")
        }

        /* Enqueue a special object to mark the end of the outgoing message
         * stream.  Output queue handler will call closeIsDone() when it pulls
         * this marker off the queue, which will be right after the last
         * message goes out.
         */
        if (amOpen) {
            enqueueSentMessage(theCloseMarker)
            amOpen = false
        }
    }

    /**
     * Cleanup and notify the message handler that all queued messages have
     * been sent and the socket closed.
     *
     * @param reason  A Throwable describing why the connection is closing.
     */
    private fun closeIsDone(reason: Throwable) {
        mySocket.close()
        myMgr.connectionCount(-1)
        if (traceFactory.comm.event) {
            traceFactory.comm.eventm("$this died: $reason")
        }
        connectionDied(reason)
    }

    /**
     * Do something when there is an error on a socket.
     *
     * This *must* be called from inside the ZMQ thread.
     */
    fun doError() {}

    /**
     * Do a read operation, given that the poller has indicated that this
     * can happen without blocking.
     *
     * This *must* be called from inside the ZMQ thread.
     */
    fun doRead() {
        try {
            var data = mySocket.recv(0)
            if (data != null) {
                var length = data.size
                while (length > 0 && data[length - 1] == 0.toByte()) {
                    --length
                }
                /* XXX Some message sources (I'm looking at you, ZWatcher) put
                   crud in front of the message; assuming said crud is purely
                   alphanumeric (a weak and dangerous assumption, but true in
                   the motivating case), we can strip it off by simply scanning
                   for the brace character that starts the message.  Yes, this
                   is a hack; we'll refactor later. */
                var start = 0
                while (data[start] != '{'.toByte() && start < length) {
                    ++start
                }
                if (start > 0) {
                    length -= start
                    val newData = ByteArray(length)
                    System.arraycopy(data, start, newData, 0, length)
                    data = newData
                }
                /* XXX end of hack */
                var nlCount = 0
                while (length > 0 && data[length - 1] == '\n'.toByte()) {
                    --length
                    ++nlCount
                }
                if (length > 0) {
                    if (nlCount < 2) {
                        myFramer.receiveBytes(data, length)
                        myFramer.receiveBytes(NEWLINES, 2)
                    } else {
                        myFramer.receiveBytes(data, length + 2)
                    }
                }
            } else {
                throw ConnectionCloseException("Null ZMQ recv result")
            }
        } catch (t: Throwable) {
            traceFactory.comm.eventm("$this problem: $t")
            close()
            closeIsDone(t)
            Runner.throwIfMandatory(t)
        }
    }

    /**
     * Do a write operation, given that the poller has indicated that this
     * can happen without blocking.
     *
     * This *must* be called from inside the ZMQ thread.
     */
    fun doWrite() { /* not Dudley */
        var closeException: Exception? = null
        try {
            val message = myOutputQueue.optDequeue()
            var outBytes: ByteArray? = null
            if (message === theCloseMarker) {
                closeException = ConnectionCloseException("Normal ZMQ connection close")
            } else if (message != null) {
                outBytes = myFramer.produceBytes(message)
            }
            if (outBytes != null) {
                mySocket.send(outBytes, 0)
            }
        } catch (e: IOException) {
            traceFactory.comm.usagem(this.toString() + " IOException: " + e.message)
            closeException = e
        }
        if (closeException != null) {
            closeIsDone(closeException)
        } else if (!myOutputQueue.hasMoreElements()) {
            myThread.unwatchSocket(mySocket)
            if (traceFactory.comm.debug) {
                traceFactory.comm.debugm("$this set poll off")
            }
        }
    }

    /**
     * Enqueue a message for output.
     *
     * @param message  The message to put on the queue; normally this will be a
     * String, but this is not required.
     */
    private fun enqueueSentMessage(message: Any) {
        if (traceFactory.comm.verbose) {
            traceFactory.comm.verbosem("enqueue $message")
        }

        /* If the connection is going away, the message can be discarded. */
        if (amOpen) {
            myOutputQueue.enqueue(message)
            var doWakeup: Boolean
            synchronized(myWakeupLock) {
                doWakeup = amNeedingToWakeupThread
                amNeedingToWakeupThread = false
            }
            if (doWakeup) {
                myThread.readyToSend(this)
            }
        }
    }

    /**
     * Get a short string for labelling this connection in log entries.
     *
     * @return a label for this connection
     */
    private fun label(): String {
        return toString()
    }

    /**
     * Receive an incoming message from the remote end.
     *
     * This is called from the ZMQ thread. Consequently, it does not
     * actually process the message but simply puts it on the run queue.
     *
     * @param message the incoming message.
     */
    override fun receiveMsg(message: Any) {
        /* Time an inbound message was last received. */
        enqueueReceivedMessage(message)
    }

    /**
     * Invoked from the ZMQ thread's work queue when the poller is ready to do
     * a write.  If this connection has pending output to send, adjusts the
     * poll set so that it will then attend to the availability of write
     * opportunities when poll() is called.
     */
    override fun run() {
        if (myOutputQueue.hasMoreElements()) {
            myThread.watchSocket(mySocket, ZMQ.Poller.POLLOUT)
            if (traceFactory.comm.debug) {
                traceFactory.comm.debugm("$this set poller for write")
            }
        }
        if (traceFactory.comm.debug) {
            traceFactory.comm.debugm("ZMQ thread interested in writes on $this")
        }
    }

    /**
     * Send a message to the other end of the connection.
     *
     * @param message  The message to be sent.
     */
    override fun sendMsg(message: Any) {
        if (amSendMode) {
            if (traceFactory.comm.debug) {
                traceFactory.comm.debugm("$this enqueueing message")
            }
            enqueueSentMessage(message)
        } else {
            traceFactory.comm.errorm(this.toString() +
                    " send on a receive-only connection: " +
                    message)
        }
    }

    /**
     * Obtain the socket this connection sits on top of.
     *
     * @return this connection's socket.
     */
    fun socket(): ZMQ.Socket {
        return mySocket
    }

    /**
     * Obtain a printable String representation of this connection.
     *
     * @return a printable representation of this connection.
     */
    override fun toString(): String {
        return "ZMQ(" + id() + ")"
    }

    /**
     * Mark this connection as needing to notify the ZMQ thread the next time
     * messages are enqueued on the connection for sending.
     */
    fun wakeupThreadForWrite() {
        synchronized(myWakeupLock) { amNeedingToWakeupThread = true }
    }

    companion object {
        /** A cached array of two newlines, in case we need to add framing to
         * an unframed received ZMQ blob.  */
        private val NEWLINES = byteArrayOf('\n'.toByte(), '\n'.toByte())
    }

    init {
        wakeupThreadForWrite()
        /* Printable form of the address this connection is connected to. */
        myFramer = framerFactory.provideFramer(this, label())
        enqueueHandlerFactory(handlerFactory)
        if (traceFactory.comm.event) {
            traceFactory.comm.eventm("$this new ZMQ connection with $remoteAddr")
        }
    }
}
