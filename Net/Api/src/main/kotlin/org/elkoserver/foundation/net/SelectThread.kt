package org.elkoserver.foundation.net

import org.elkoserver.foundation.run.Queue
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import scalablessl.SSLSelector
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.time.Clock
import java.util.concurrent.Callable
import javax.net.ssl.SSLContext

/**
 * A thread for doing all network I/O operations (send, receive, accept) using
 * non-blocking Channels.  Requests to open listeners, connect to remote hosts,
 * and send messages on connections are all fed to this thread via a queue.
 * This thread in turn feeds received input events to the run queue.
 *
 * @param myMgr  Network manager for this server.
 * @param sslContext  SSL context to use, if supporting SSL, else null
 */
internal class SelectThread(
        private val myMgr: NetworkManager, sslContext: SSLContext?, private val clock: Clock, private val traceFactory: TraceFactory) : Thread("Elko Select") {
    /** Selector to await available I/O opportunities.  */
    private var mySelector: Selector? = null

    /** Queue of unserviced I/O requests.  */
    private val myQueue = Queue<Any>()

    /**
     * The body of the thread.  Responsible for dequeueing I/O requests and
     * acting upon them, and for selecting over the currently open set of
     * sockets.
     */
    override fun run() {
        if (traceFactory.comm.debug) {
            traceFactory.comm.debugm("select thread running")
        }
        while (true) {
            try {
                val selectedCount = mySelector!!.select()
                if (traceFactory.comm.debug) {
                    traceFactory.comm.debugm("select() returned with count=$selectedCount")
                }
                var workToDo = myQueue.optDequeue()
                while (workToDo != null) {
                    if (workToDo is Listener) {
                        val listener = workToDo
                        listener.register(this, mySelector)
                        if (traceFactory.comm.debug) {
                            traceFactory.comm.debugm(
                                    "select thread registers listener $listener")
                        }
                    } else if (workToDo is Callable<*>) {
                        workToDo.call()
                    } else {
                        traceFactory.comm.errorm("mystery object on select queue: $workToDo")
                    }
                    workToDo = myQueue.optDequeue()
                }
                if (selectedCount > 0) {
                    val iter = mySelector!!.selectedKeys().iterator()
                    var actualCount = 0
                    while (iter.hasNext()) {
                        ++actualCount
                        val key = iter.next()
                        iter.remove()
                        if (key.isValid && key.isAcceptable) {
                            val listener = key.attachment() as Listener
                            if (traceFactory.comm.debug) {
                                traceFactory.comm.debugm("select has accept for $listener")
                            }
                            listener.doAccept()
                        }
                        if (key.isValid && key.isReadable) {
                            val connection = key.attachment() as TCPConnection
                            if (traceFactory.comm.debug) {
                                traceFactory.comm.debugm("select has read for $connection")
                            }
                            connection.doRead()
                        }
                        if (key.isValid && key.isWritable) {
                            val connection = key.attachment() as TCPConnection
                            connection.wakeupSelectForWrite()
                            if (traceFactory.comm.debug) {
                                traceFactory.comm.debugm("select has write for $connection")
                            }
                            connection.doWrite()
                        }
                    }
                    if (traceFactory.comm.debug) {
                        if (actualCount > 0) {
                            traceFactory.comm.debugm("select thread selected $actualCount/$selectedCount I/O sources")
                        }
                    }
                }
            } catch (e: Throwable) {
                traceFactory.comm.errorm("select failed", e)
            }
        }
    }

    /**
     * Make a new outbound TCPConnection to another host on the net.
     *
     * @param handlerFactory  Provider of a message handler to process messages
     * received on the new connection.
     * @param framerFactory  Byte I/O framer factory for the new connection.
     * @param remoteAddr  Host name and port number to connect to.
     * @param trace  Trace object to use for activity on the new connectoin.
     */
    fun connect(handlerFactory: MessageHandlerFactory,
                framerFactory: ByteIOFramerFactory?,
                remoteAddr: String, trace: Trace?) {
        myQueue.enqueue(Callable<Any?> {
            try {
                val remoteNetAddr = NetAddr(remoteAddr)
                val socketAddress = InetSocketAddress(remoteNetAddr.inetAddress,
                        remoteNetAddr.port)
                traceFactory.comm.eventi("connecting to $remoteNetAddr")
                val channel = SocketChannel.open(socketAddress)
                newChannel(handlerFactory, framerFactory, channel, false,
                        trace)
            } catch (e: IOException) {
                myMgr.connectionCount(-1)
                traceFactory.comm.errorm("unable to connect to $remoteAddr: $e")
                handlerFactory.provideMessageHandler(null)
            }
            null
        })
        mySelector!!.wakeup()
    }

    /**
     * Begin listening for inbound TCP connections on some port.
     *
     * @param localAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide the handlers
     * for connections made to this port.
     * @param framerFactory  Byte I/O framer factory for new connections.
     * @param secure  If true, use SSL.
     * @param portTrace  Trace object for logging activity associated with this
     * port & its connections
     */
    @Throws(IOException::class)
    fun listen(localAddress: String?, handlerFactory: MessageHandlerFactory?,
               framerFactory: ByteIOFramerFactory?, secure: Boolean,
               portTrace: Trace?): Listener {
        val listener = Listener(localAddress!!, handlerFactory!!,
                framerFactory!!, myMgr, secure, portTrace!!)
        myQueue.enqueue(listener)
        mySelector!!.wakeup()
        return listener
    }

    /**
     * Handle the establishment of a new connection as the result of an
     * outbound connection made to another host or an accept operation by a
     * listener.
     *
     * @param handlerFactory  Message handler factory to provide the handlers
     * for the new connection.
     * @param framerFactory  Byte I/O framer factory for the new connection.
     * @param channel  The new channel for the new connection.
     * @param isSecure  If true, this will be an SSL connnection.
     * @param trace  Trace object to use with this new connection.
     */
    fun newChannel(handlerFactory: MessageHandlerFactory,
                   framerFactory: ByteIOFramerFactory?,
                   channel: SocketChannel, isSecure: Boolean, trace: Trace?) {
        try {
            channel.configureBlocking(false)
            val key = channel.register(mySelector, SelectionKey.OP_READ)
            key.attach(TCPConnection(handlerFactory, framerFactory!!,
                    channel, key, this, myMgr, isSecure, trace!!, clock, traceFactory))
        } catch (e: ClosedChannelException) {
            myMgr.connectionCount(-1)
            handlerFactory.provideMessageHandler(null)
            traceFactory.comm.errorm("channel closed before it could be used", e)
        } catch (e: IOException) {
            myMgr.connectionCount(-1)
            handlerFactory.provideMessageHandler(null)
            traceFactory.comm.errorm("trouble opening TCPConnection for channel", e)
            try {
                channel.close()
            } catch (e2: IOException) {
                /* So if close fails, we're supposed to do what?  Close? */
            }
        }
    }

    /**
     * Notify this thread that a connection now has messages queued ready for
     * transmission.
     *
     * @param connection  The connection that has messages ready to send.
     */
    fun readyToSend(connection: TCPConnection) {
        if (traceFactory.comm.debug) {
            traceFactory.comm.debugm("$connection ready to send")
        }
        myQueue.enqueue(connection)
        mySelector!!.wakeup()
    }

    init {
        try {
            mySelector = if (sslContext != null) {
                SSLSelector.open(sslContext)
            } else {
                Selector.open()
            }
            start()
        } catch (e: IOException) {
            traceFactory.comm.errorm("failed to start SelectThread", e)
        }
    }
}
