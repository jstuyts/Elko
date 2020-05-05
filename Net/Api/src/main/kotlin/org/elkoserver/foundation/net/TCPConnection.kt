package org.elkoserver.foundation.net

import org.elkoserver.foundation.run.Queue
import org.elkoserver.foundation.run.Runner.Companion.throwIfMandatory
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.time.Clock
import java.util.concurrent.Callable

/**
 * An implementation of [Connection] that manages a non-blocking TCP
 * connection to a single remote host.
 *
 * This constructor *must* be called from inside the select thread.
 *
 * @param handlerFactory  Provider of a message handler to process messages
 * received on this connection.
 * @param framerFactory  Byte I/O framer factory for the connection.
 * @param channel  Channel to the TCP connection proper.
 * @param key  Selection key for reads and writes over 'channel'.
 * @param selectThread  Select thread that is managing this connection.
 * @param mgr  Network manager for this server.
 * @param amSecure  If true, this is an SSL connection.
 * @param myTrace  Trace object to use for this connection.
 */
class TCPConnection internal constructor(handlerFactory: MessageHandlerFactory,
                                         framerFactory: ByteIOFramerFactory, channel: SocketChannel,
                                         key: SelectionKey, selectThread: SelectThread,
                                         mgr: NetworkManager,
                                         private val amSecure: Boolean,
                                         private val myTrace: Trace, clock: Clock, traceFactory: TraceFactory) : ConnectionBase(mgr, clock, traceFactory), MessageReceiver, Callable<Any?> {
    /** Queue of unencoded outbound messages.  */
    private val myOutputQueue: Queue<Any>

    /** Framer to perform low-level message conversion.  */
    private val myFramer: ByteIOFramer

    /** Buffer holding actual output bytes.  */
    private var myOutputBuffer: ByteBuffer?

    /** Thread that does blocking select operations for this connection.  */
    private val mySelectThread: SelectThread

    /** Channel for sending and receiving over.  */
    private val myChannel: SocketChannel

    /** Selection key for testing the readability and writability of
     * 'myChannel'.  */
    private val myKey: SelectionKey

    /** Buffer receiving actual input bytes.  */
    private val myInputBuffer: ByteBuffer

    /** Monitor lock for syncing with the select thread.  */
    private val myWakeupLock = Any()

    /** Flag to trigger select thread to look for write opportunities.  */
    private var amNeedingToWakeupSelect = false

    /** Flag that is true until the connection is closed.  */
    private var amOpen: Boolean

    /** Network manager for this server.  */
    private val myMgr: NetworkManager

    /**
     * Invoked from the selector thread's work queue when the selector is ready
     * to do a write.  If this connection has pending output to send, adjusts
     * the selection key so that it will then attend to the availability of
     * write opportunities when select() is called.
     */
    override fun call(): Any? {
        if (myOutputQueue.hasMoreElements() && myKey.isValid) {
            myKey.interestOps(myKey.interestOps() or SelectionKey.OP_WRITE)
            if (myTrace.debug) {
                myTrace.debugm("$this set selectkey Read/Write")
            }
        }
        if (myTrace.debug) {
            myTrace.debugm("select thread interested in writes on $this")
        }
        return null
    }

    /**
     * Shut down the connection.  Any queued messages will be sent.
     */
    override fun close() {
        if (myTrace.debug) {
            myTrace.debugm("$this close")
        }

        /* Enqueue a special object to mark the end of the outgoing message
         * stream.  Output queue handler will call closeIsDone() when it pulls
         * this marker off the queue, which will be right after the last
         * message goes out.
         */if (amOpen) {
            enqueueSentMessage(theCloseMarker)
            amOpen = false
        }
    }

    /**
     * Cleanup and notify the message handler that all queued messages have
     * been sent and the channel closed.
     *
     * @param reason  A Throwable describing why the connection is closing.
     */
    private fun closeIsDone(reason: Throwable) {
        try {
            myChannel.close()
        } catch (e: IOException) {
            /* Throwing an IOException on connection close has got to be one of
               the stupidest things -- what're you gonna do about it? Close the
               connection? */
            myTrace.debugm("$this ignoring IOException on close")
        }
        myKey.attach(null)
        myMgr.connectionCount(-1)
        if (myTrace.event) {
            myTrace.eventi("$this died: $reason")
        }
        var message = myOutputQueue.optDequeue()
        while (message != null) {
            if (message is Releasable) {
                message.release()
            }
            message = myOutputQueue.optDequeue()
        }
        connectionDied(reason)
    }

    /**
     * Do a read() operation, given that the selector has indicated that this
     * can happen without blocking.
     *
     * This *must* be called from inside the select thread.
     */
    fun doRead() {
        try {
            var count: Int
            do {
                count = myChannel.read(myInputBuffer)
                if (count < 0) {
                    /* EOF: cease to be interested in reads, then throw EOF. */
                    myKey.interestOps(myKey.interestOps() and SelectionKey.OP_READ.inv())
                    throw EOFException()
                } else {
                    /* Data read: give bytes to framer, then recycle the buffer. */
                    if (count > 0) {
                        myFramer.receiveBytes(myInputBuffer.array(),
                                myInputBuffer.position())
                        myInputBuffer.clear()
                    } else {
                        if (myTrace.event) {
                            myTrace.debugm("$this zero length read")
                        }
                    }
                }
            } while (count > 0 && amSecure)
        } catch (t: Throwable) {
            /* If anything bad happens during read, the connection is dead. */
            if (myTrace.debug) {
                myTrace.debugm("$this caught exception", t)
            }
            if (t is EOFException) {
                myTrace.eventi("$this remote disconnect")
            } else if (t is IOException) {
                myTrace.usagei("$this IOException: ${t.message}")
            } else {
                myTrace.errorm("$this Error", t)
            }
            close()
            /* Kill it immediately: if the connection is dead, the write queue
               will never be processed, so orderly close will never finish. */closeIsDone(t)
            throwIfMandatory(t)
        }
    }

    /**
     * Do a write() operation, given that the selector has indicated that this
     * can happen without blocking.
     *
     * This *must* be called from inside the select thread.
     */
    fun doWrite() { /* not Dudley */
        var closeException: Exception? = null
        try {
            if (myOutputBuffer == null) {
                val message = myOutputQueue.optDequeue()
                if (message === theCloseMarker) {
                    closeException = ConnectionCloseException(
                            "Normal TCP connection close")
                } else if (message != null) {
                    myOutputBuffer = ByteBuffer.wrap(myFramer.produceBytes(message))
                    if (message is Releasable) {
                        message.release()
                    }
                }
            }
            if (myOutputBuffer != null) {
                val before = myOutputBuffer!!.remaining()
                val wrote = myChannel.write(myOutputBuffer)
                if (myTrace.event) {
                    myTrace.debugm("$this wrote $wrote bytes of $before")
                }
                if (myOutputBuffer!!.remaining() == 0) {
                    myOutputBuffer = null
                }
            } else if (amSecure) {
                /* ScalableSSL sometimes requires us to do empty writes to pump
                   SSL protocol handshaking. */
                if (myTrace.event) {
                    myTrace.debugm("$this SSL empty write")
                }
                myChannel.write(theEmptyBuffer)
            }
        } catch (e: IOException) {
            myTrace.usagem("$this IOException: ${e.message}")
            closeException = e
        }
        if (closeException != null) {
            closeIsDone(closeException)
        } else if (myOutputBuffer == null) {
            if (!myOutputQueue.hasMoreElements()) {
                myKey.interestOps(myKey.interestOps() and SelectionKey.OP_WRITE.inv())
                if (myTrace.debug) {
                    myTrace.debugm("$this set selectkey ReadOnly")
                }
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
        if (myTrace.verbose) {
            myTrace.verbosem("enqueue $message")
        }

        /* If the connection is going away, the message can be discarded. */if (amOpen) {
            myOutputQueue.enqueue(message)
            var doWakeup: Boolean
            synchronized(myWakeupLock) {
                doWakeup = amNeedingToWakeupSelect
                amNeedingToWakeupSelect = false
            }
            if (doWakeup) {
                mySelectThread.readyToSend(this)
            }
        } else {
            if (message is Releasable) {
                message.release()
            }
        }
    }/* We are looking at myOutputBuffer from outside the thread that is
               actually entitled to be looking at it, so it is possible that
               the variable will be non-null when we test it for null and then
               null a moment later when we try to invoke hasRemaining() on
               it. Since the purpose of this method is only to give an
               approximate take on the writabilty of the connection, we can
               declare that if this NPE happens then the buffer is now null and
               hence writable.  We could be more disciplined about locking and
               such, but it would be huge increase in thread madness with no
               particular benefit gained as a consequence.  So this little bit
               of meatball hackery seems like it's actually the least dumb
               thing to do here (aside from arranging to not want to ask the
               "is it writable?"  question in the first place, of course; but
               that would be even more work.) */

    /**
     * Test if this connection is available for writes.
     *
     * The idea here is to get an approximate sense, when arbitrating among a
     * series of alternate possible connections to transmit something over.  Do
     * not do anything that depends for its correctness on the answer returned
     * by this method being accurate.
     *
     * @return true if this connection appears to be writable, false if not.
     */
    val isWritable: Boolean
        get() = try {
            amOpen &&
                    !myOutputQueue.hasMoreElements() &&
                    (myOutputBuffer == null || !myOutputBuffer!!.hasRemaining())
        } catch (e: NullPointerException) {
            /* We are looking at myOutputBuffer from outside the thread that is
               actually entitled to be looking at it, so it is possible that
               the variable will be non-null when we test it for null and then
               null a moment later when we try to invoke hasRemaining() on
               it. Since the purpose of this method is only to give an
               approximate take on the writabilty of the connection, we can
               declare that if this NPE happens then the buffer is now null and
               hence writable.  We could be more disciplined about locking and
               such, but it would be huge increase in thread madness with no
               particular benefit gained as a consequence.  So this little bit
               of meatball hackery seems like it's actually the least dumb
               thing to do here (aside from arranging to not want to ask the
               "is it writable?"  question in the first place, of course; but
               that would be even more work.) */
            true
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
     * This is called from the select thread. Consequently, it does not
     * actually process the message but simply puts it on the run queue.
     *
     * @param message the incoming message.
     */
    override fun receiveMsg(message: Any) {
        enqueueReceivedMessage(message)
    }

    /**
     * Send a message to the other end of the connection.
     *
     * @param message  The message to be sent.
     */
    override fun sendMsg(message: Any) {
        if (myTrace.debug) {
            myTrace.debugm("$this enqueueing message: $message")
        }
        enqueueSentMessage(message)
    }

    /**
     * Obtain a printable String representation of this connection.
     *
     * @return a printable representation of this connection.
     */
    override fun toString(): String {
        return "TCP(${id()})"
    }

    /**
     * Mark this connection as needing to notify the select thread the next
     * time messages are enqueued on the connection for sending.
     */
    fun wakeupSelectForWrite() {
        synchronized(myWakeupLock) { amNeedingToWakeupSelect = true }
    }

    companion object {
        /** Default size of input buffer.  */
        private const val INPUT_BUFFER_SIZE = 2048

        /** Empty buffer, for empty sends used to pump SSL logic.  */
        private val theEmptyBuffer = ByteBuffer.wrap(ByteArray(0))
    }

    init {
        wakeupSelectForWrite()
        myChannel = channel
        myKey = key
        myMgr = mgr
        amOpen = true
        channel.configureBlocking(false)
        val socket = channel.socket()
        socket.setSoLinger(true, 0)
        socket.reuseAddress = true
        val myRemoteAddr = "${socket.inetAddress.hostAddress}:${socket.port}"
        myInputBuffer = ByteBuffer.wrap(ByteArray(INPUT_BUFFER_SIZE))
        myFramer = framerFactory.provideFramer(this, label())
        myOutputBuffer = null
        myOutputQueue = Queue()
        mySelectThread = selectThread
        enqueueHandlerFactory(handlerFactory)
        if (myTrace.event) {
            myTrace.eventi("$this new connection from $myRemoteAddr")
        }
    }
}