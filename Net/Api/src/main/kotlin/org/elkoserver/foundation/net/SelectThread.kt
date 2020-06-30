package org.elkoserver.foundation.net

import org.elkoserver.foundation.byteioframer.ByteIOFramerFactory
import org.elkoserver.foundation.run.Queue
import org.elkoserver.foundation.run.Runner
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.slf4j.Gorgel
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
 * @param sslContext  SSL context to use, if supporting SSL, else null
 */
class SelectThread(
        private val runner: Runner,
        private val loadMonitor: LoadMonitor,
        sslContext: SSLContext?,
        private val clock: Clock,
        private val commGorgel: Gorgel,
        private val tcpConnectionGorgel: Gorgel,
        private val tcpConnectionCommGorgel: Gorgel,
        private val idGenerator: IdGenerator,
        private val listenerFactory: ListenerFactory)
    : Thread("Elko Select") {
    /** Selector to await available I/O opportunities.  */
    private val mySelector: Selector

    /** Queue of unserviced I/O requests.  */
    private val myQueue = Queue<Any>()

    private var mustStop = false

    /**
     * The body of the thread.  Responsible for dequeueing I/O requests and
     * acting upon them, and for selecting over the currently open set of
     * sockets.
     */
    override fun run() {
        commGorgel.d?.run { debug("select thread running") }
        while (!mustStop) {
            try {
                val selectedCount = mySelector.select()
                commGorgel.d?.run { debug("select() returned with count=$selectedCount") }
                var workToDo = myQueue.optDequeue()
                while (workToDo != null) {
                    if (workToDo is Listener) {
                        val listener = workToDo
                        listener.register(this, mySelector)
                        commGorgel.d?.run { debug("select thread registers listener $listener") }
                    } else if (workToDo is Callable<*>) {
                        workToDo.call()
                    } else {
                        commGorgel.error("mystery object on select queue: $workToDo")
                    }
                    workToDo = myQueue.optDequeue()
                }
                if (selectedCount > 0) {
                    val iter = mySelector.selectedKeys().iterator()
                    var actualCount = 0
                    while (iter.hasNext()) {
                        ++actualCount
                        val key = iter.next()
                        iter.remove()
                        if (key.isValid && key.isAcceptable) {
                            val listener = key.attachment() as Listener
                            commGorgel.d?.run { debug("select has accept for $listener") }
                            listener.doAccept()
                        }
                        if (key.isValid && key.isReadable) {
                            val connection = key.attachment() as TCPConnection
                            commGorgel.d?.run { debug("select has read for $connection") }
                            connection.doRead()
                        }
                        if (key.isValid && key.isWritable) {
                            val connection = key.attachment() as TCPConnection
                            connection.wakeupSelectForWrite()
                            commGorgel.d?.run { debug("select has write for $connection") }
                            connection.doWrite()
                        }
                    }
                    if (actualCount > 0) {
                        commGorgel.d?.run { debug("select thread selected $actualCount/$selectedCount I/O sources") }
                    }
                }
            } catch (e: Throwable) {
                commGorgel.error("select failed", e)
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
     */
    fun connect(handlerFactory: MessageHandlerFactory,
                framerFactory: ByteIOFramerFactory,
                remoteAddr: String) {
        myQueue.enqueue(Callable<Any?> {
            try {
                val remoteNetAddr = NetAddr(remoteAddr)
                val socketAddress = InetSocketAddress(remoteNetAddr.inetAddress,
                        remoteNetAddr.port)
                commGorgel.i?.run { info("connecting to $remoteNetAddr") }
                val channel = SocketChannel.open(socketAddress)
                newChannel(handlerFactory, framerFactory, channel, false)
            } catch (e: IOException) {
                commGorgel.error("unable to connect to $remoteAddr: $e")
                handlerFactory.provideMessageHandler(null)
            }
            null
        })
        mySelector.wakeup()
    }

    /**
     * Begin listening for inbound TCP connections on some port.
     *
     * @param localAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide the handlers
     * for connections made to this port.
     * @param framerFactory  Byte I/O framer factory for new connections.
     * @param secure  If true, use SSL.
     */
    @Throws(IOException::class)
    fun listen(localAddress: String, handlerFactory: MessageHandlerFactory,
               framerFactory: ByteIOFramerFactory, secure: Boolean): Listener {
        val listener = listenerFactory.create(localAddress, handlerFactory, framerFactory, secure)
        myQueue.enqueue(listener)
        mySelector.wakeup()
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
     */
    fun newChannel(handlerFactory: MessageHandlerFactory,
                   framerFactory: ByteIOFramerFactory,
                   channel: SocketChannel, isSecure: Boolean) {
        try {
            channel.configureBlocking(false)
            val key = channel.register(mySelector, SelectionKey.OP_READ)
            key.attach(TCPConnection(handlerFactory, framerFactory,
                    channel, key, this, runner, loadMonitor, isSecure, tcpConnectionGorgel, clock, tcpConnectionCommGorgel, idGenerator))
        } catch (e: ClosedChannelException) {
            handlerFactory.provideMessageHandler(null)
            commGorgel.error("channel closed before it could be used", e)
        } catch (e: IOException) {
            handlerFactory.provideMessageHandler(null)
            commGorgel.error("trouble opening TCPConnection for channel", e)
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
        commGorgel.d?.run { debug("$connection ready to send") }
        myQueue.enqueue(connection)
        mySelector.wakeup()
    }

    fun shutDown() {
        mustStop = true
        interrupt()
    }

    init {
        try {
            mySelector = if (false && sslContext != null) {
//                SSLSelector.open(sslContext)
                throw IllegalStateException()
            } else {
                Selector.open()
            }
            start()
        } catch (e: IOException) {
            commGorgel.error("failed to start SelectThread", e)
            throw IllegalStateException(e)
        }
    }
}
