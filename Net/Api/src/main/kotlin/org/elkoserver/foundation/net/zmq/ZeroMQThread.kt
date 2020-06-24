package org.elkoserver.foundation.net.zmq

import org.elkoserver.foundation.net.ByteIOFramerFactory
import org.elkoserver.foundation.net.LoadMonitor
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.run.Queue
import org.elkoserver.foundation.run.Runner
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.zeromq.SocketType
import org.zeromq.ZMQ
import java.io.IOException
import java.time.Clock

class ZeroMQThread(
        private val runner: Runner,
        private val loadMonitor: LoadMonitor,
        private val connectionCommGorgel: Gorgel,
        private val traceFactory: TraceFactory,
        private val idGenerator: IdGenerator,
        private val clock: Clock) : Thread("Elko ZeroMQ") {
    /** Queue of unserviced I/O requests.  */
    private val myQueue = Queue<Any>()

    /** ZeroMQ context for all operations.  */
    private val myContext = ZMQ.context(1)

    /** Poller to await available I/O opportunities.  */
    private val myPoller = myContext.poller()

    /** Internal socket for sending signal to thread.  */
    private val mySignalSendSocket = myContext.socket(SocketType.PAIR)

    /** Internal socket for receiving signal to thread.  */
    private val mySignalRecvSocket = myContext.socket(SocketType.PAIR)

    /** Poller index of internal socket for thread to receive signals.  */
    private val mySignalRecvSocketIndex = myPoller.register(mySignalRecvSocket, ZMQ.Poller.POLLIN)

    /** Open connections, by socket.  */
    private val myConnections: MutableMap<ZMQ.Socket, ZeroMQConnection> = HashMap()

    /**
     * The body of the thread.  Responsible for dequeueing I/O requests and
     * acting upon them, and for polling the currently open set of sockets.
     */
    override fun run() {
        if (traceFactory.comm.debug) {
            traceFactory.comm.debugm("ZMQ thread running")
        }
        while (true) {
            try {
                val selectedCount = myPoller.poll().toLong()
                var workToDo = myQueue.optDequeue()
                while (workToDo != null) {
                    if (workToDo is Runnable) {
                        workToDo.run()
                    } else {
                        traceFactory.comm.errorm("non-Runnable on ZMQ queue: $workToDo")
                    }
                    workToDo = myQueue.optDequeue()
                }
                if (selectedCount > 0) {
                    var actualCount = 0
                    val maxIndex = myPoller.size
                    for (i in 0 until maxIndex) {
                        if (i == mySignalRecvSocketIndex) {
                            if (myPoller.pollin(i)) {
                                /* Just a wakeup, no actual work to do */
                                ++actualCount
                                mySignalRecvSocket.recv(0)
                            }
                        } else if (myPoller.pollerr(i)) {
                            ++actualCount
                            val connection = getConnection(i)
                            if (traceFactory.comm.debug) {
                                traceFactory.comm.debugm("poll has error for $connection")
                            }
                            connection.doError()
                        } else if (myPoller.pollin(i)) {
                            ++actualCount
                            val connection = getConnection(i)
                            if (traceFactory.comm.debug) {
                                traceFactory.comm.debugm("poll has read for $connection")
                            }
                            connection.doRead()
                        } else if (myPoller.pollout(i)) {
                            ++actualCount
                            val connection = getConnection(i)
                            connection.wakeupThreadForWrite()
                            if (traceFactory.comm.debug) {
                                traceFactory.comm.debugm("poll has write for $connection")
                            }
                            connection.doWrite()
                        }
                    }
                    if (traceFactory.comm.debug) {
                        traceFactory.comm.debugm("ZMQ thread poll selects $actualCount/$selectedCount I/O sources")
                    }
                }
            } catch (e: Throwable) {
                traceFactory.comm.errorm("polling loop failed", e)
            }
        }
    }

    private fun getConnection(socketIndex: Int) = myConnections[myPoller.getSocket(socketIndex)]
            ?: throw IllegalStateException("No connection for socket: $socketIndex")

    /**
     * Add a socket to the set being polled.
     *
     * @param socket  The socket to add
     * @param mask  Mask indicating the kind of I/O availability of interest.
     */
    fun watchSocket(socket: ZMQ.Socket?, mask: Int) {
        myPoller.register(socket, mask or ZMQ.Poller.POLLERR)
    }

    /**
     * Remove a socket from the set being polled.
     *
     * @param socket  The socket that is no longer interesting.
     */
    fun unwatchSocket(socket: ZMQ.Socket?) {
        myPoller.unregister(socket)
    }

    /**
     * Interrupt the poll() call when there is work to do but no external I/O
     * availability that would otherwise cause it to return.
     */
    private fun wakeup() {
        mySignalSendSocket.send(EMPTY_MESSAGE, 0)
    }

    /**
     * Make a new outbound ZeroMQ connection to another host on the net.
     *
     * @param handlerFactory  Provider of a message handler to process messages
     * received on a new connection; since outbound ZeroMQ connections are
     * send-only, no messages will ever be received, but the handler factory
     * is also the callback that is notified about new connection
     * establishment.
     * @param framerFactory  Byte I/O framer factory for the new connection
     * @param remoteAddr  Host name and port number to connect to.
     */
    fun connect(handlerFactory: MessageHandlerFactory, framerFactory: ByteIOFramerFactory, remoteAddr: String) {
        val push: Boolean
        val finalAddr: String
        if (remoteAddr.startsWith("PUSH:")) {
            push = true
            finalAddr = "tcp://${remoteAddr.substring(5)}"
        } else if (remoteAddr.startsWith("PUB:")) {
            push = false
            finalAddr = try {
                val parsedAddr = NetAddr(remoteAddr.substring(4))
                "tcp://*:${parsedAddr.port}"
            } catch (e: IOException) {
                traceFactory.comm.errorm("error setting up ZMQ connection with $remoteAddr: $e")
                return
            }
        } else {
            push = true
            finalAddr = "tcp://$remoteAddr"
        }
        myQueue.enqueue(object : Runnable {
            override fun run() {
                traceFactory.comm.eventm("connecting ZMQ to $finalAddr")
                val socket: ZMQ.Socket
                if (push) {
                    socket = myContext.socket(SocketType.PUSH)
                    socket.connect(finalAddr)
                } else {
                    socket = myContext.socket(SocketType.PUB)
                    socket.bind(finalAddr)
                }
                val connection = ZeroMQConnection(handlerFactory, framerFactory,
                        socket, true, this@ZeroMQThread,
                        runner, loadMonitor, finalAddr, clock, connectionCommGorgel, idGenerator)
                myConnections[socket] = connection
            }
        })
        wakeup()
    }

    /**
     * Terminate an open ZeroMQ connection.
     *
     * @param connection  The connection whose closure is desired.
     */
    fun close(connection: ZeroMQConnection) {
        myQueue.enqueue(object : Runnable {
            override fun run() {
                traceFactory.comm.eventm("closing ZMQ connection $connection")
                val socket = connection.socket()
                unwatchSocket(socket)
                myConnections.remove(socket)
            }
        })
        wakeup()
    }

    /**
     * Begin listening for inbound ZeroMQ connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide the handlers
     * for connections made to this port.
     * @param framerFactory  Byte I/O framer factory for new connections.
     * @param secure  If true, use a secure connection pathway (e.g., SSL).
     *
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listen(listenAddress: String, handlerFactory: MessageHandlerFactory, framerFactory: ByteIOFramerFactory, secure: Boolean): NetAddr {
        var actualListenAddress = listenAddress
        if (secure) {
            throw Error("secure ZeroMQ not yet available")
        }
        val subscribe: Boolean
        if (actualListenAddress.startsWith("SUB:")) {
            subscribe = true
            actualListenAddress = actualListenAddress.substring(4)
        } else if (actualListenAddress.startsWith("PULL:")) {
            subscribe = false
            actualListenAddress = actualListenAddress.substring(5)
        } else {
            subscribe = true
        }
        val result = NetAddr(actualListenAddress)
        val finalAddress: String
        finalAddress = if (subscribe) {
            "tcp://$actualListenAddress"
        } else {
            "tcp://*:${result.port}"
        }
        myQueue.enqueue(object : Runnable {
            override fun run() {
                val socket: ZMQ.Socket
                if (subscribe) {
                    traceFactory.comm.eventm("subscribing to ZMQ messages from $finalAddress")
                    socket = myContext.socket(SocketType.SUB)
                    socket.subscribe(UNIVERSAL_SUBSCRIPTION)
                    socket.connect(finalAddress)
                } else {
                    traceFactory.comm.eventm("pulling ZMQ messages at $finalAddress")
                    socket = myContext.socket(SocketType.PULL)
                    socket.bind(finalAddress)
                }
                traceFactory.comm.eventm("ZMQ socket initialized")
                val connection = ZeroMQConnection(handlerFactory, framerFactory,
                        socket, false, this@ZeroMQThread,
                        runner, loadMonitor, "*", clock, connectionCommGorgel, idGenerator)
                myConnections[socket] = connection
                traceFactory.comm.eventm("watching ZMQ socket")
                watchSocket(socket, ZMQ.Poller.POLLIN)
            }
        })
        wakeup()
        return result
    }

    /**
     * Notify this thread that a connection now has messages queued ready for
     * transmission.
     *
     * @param connection  The connection that has messages ready to send.
     */
    fun readyToSend(connection: ZeroMQConnection) {
        myQueue.enqueue(connection)
        wakeup()
    }

    companion object {
        /** Internal connect point for signalling thread via ZMQ  */
        private const val ZMQ_SIGNAL_ADDR = "inproc://zmqSignal"

        /** Empty message, for signalling the thread.  */
        private val EMPTY_MESSAGE = ByteArray(0)

        /** Subscribe filter to receive all messages.  */
        private val UNIVERSAL_SUBSCRIPTION = ByteArray(0)
    }

    init {
        mySignalRecvSocket.bind(ZMQ_SIGNAL_ADDR)
        mySignalSendSocket.connect(ZMQ_SIGNAL_ADDR)
        start()
    }
}
