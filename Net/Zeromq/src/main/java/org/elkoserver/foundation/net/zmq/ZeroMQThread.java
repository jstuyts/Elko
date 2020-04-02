package org.elkoserver.foundation.net.zmq;

import org.elkoserver.foundation.net.ByteIOFramerFactory;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NetAddr;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.foundation.run.Queue;
import org.elkoserver.util.trace.TraceFactory;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

class ZeroMQThread extends Thread {
    /** Poller to await available I/O opportunities. */
    private ZMQ.Poller myPoller;

    /** Queue of unserviced I/O requests. */
    private Queue<Object> myQueue;
    
    /** Network manager for this server */
    private NetworkManager myNetworkManager;
    private TraceFactory traceFactory;

    /** ZeroMQ context for all operations. */
    private ZMQ.Context myContext;
    
    /** Internal connect point for signalling thread via ZMQ */
    private static final String ZMQ_SIGNAL_ADDR = "inproc://zmqSignal";

    /** Internal socket for sending signal to thread. */
    private ZMQ.Socket mySignalSendSocket;

    /** Internal socket for receiving signal to thread. */
    private ZMQ.Socket mySignalRecvSocket;

    /** Poller index of internal socket for thread to receive signals. */
    private int mySignalRecvSocketIndex;

    /** Empty message, for signalling the thread. */
    private static final byte[] EMPTY_MESSAGE = new byte[0];

    /** Subscribe filter to receive all messages. */
    private static final byte[] UNIVERSAL_SUBSCRIPTION = new byte[0];

    /** Open connections, by socket. */
    private Map<ZMQ.Socket, ZeroMQConnection> myConnections;
    private final Clock clock;

    /**
     * Constructor.
     *
     * @param mgr  Network manager for this server.
     */
    ZeroMQThread(NetworkManager mgr, TraceFactory traceFactory, Clock clock) {
        super("Elko ZeroMQ");
        myNetworkManager = mgr;
        this.traceFactory = traceFactory;
        this.clock = clock;
        myQueue = new Queue<>();
        myConnections = new HashMap<>();
        myContext = ZMQ.context(1);
        myPoller = myContext.poller();
        mySignalRecvSocket = myContext.socket(SocketType.PAIR);
        mySignalRecvSocket.bind(ZMQ_SIGNAL_ADDR);
        mySignalRecvSocketIndex =
            myPoller.register(mySignalRecvSocket, ZMQ.Poller.POLLIN);
        mySignalSendSocket = myContext.socket(SocketType.PAIR);
        mySignalSendSocket.connect(ZMQ_SIGNAL_ADDR);

        start();
    }
    
    /**
     * The body of the thread.  Responsible for dequeueing I/O requests and
     * acting upon them, and for polling the currently open set of sockets.
     */
    public void run() {
        if (traceFactory.comm.getDebug()) {
            traceFactory.comm.debugm("ZMQ thread running");
        }
        while (true) {
            try {
                long selectedCount = myPoller.poll();
                
                Object workToDo = myQueue.optDequeue();
                while (workToDo != null) {
                    if (workToDo instanceof Thunk) {
                        Thunk thunk = (Thunk) workToDo;
                        thunk.run();
                    } else {
                        traceFactory.comm.errorm("non-Thunk on ZMQ queue: " +
                                          workToDo);
                    }
                    workToDo = myQueue.optDequeue();
                }
                
                if (selectedCount > 0) {
                    int actualCount = 0;
                    int maxIndex = myPoller.getSize();
                    for (int i = 0; i < maxIndex; ++i) {
                        if (i == mySignalRecvSocketIndex) {
                            if (myPoller.pollin(i)) {
                                /* Just a wakeup, no actual work to do */
                                ++actualCount;
                                mySignalRecvSocket.recv(0);
                            }
                        } else if (myPoller.pollerr(i)) {
                            ++actualCount;
                            ZeroMQConnection connection =
                                myConnections.get(myPoller.getSocket(i));
                            if (traceFactory.comm.getDebug()) {
                                traceFactory.comm.debugm("poll has error for " +
                                                  connection);
                            }
                            connection.doError();
                        } else if (myPoller.pollin(i)) {
                            ++actualCount;
                            ZeroMQConnection connection =
                                myConnections.get(myPoller.getSocket(i));
                            if (traceFactory.comm.getDebug()) {
                                traceFactory.comm.debugm("poll has read for " +
                                                  connection);
                            }
                            connection.doRead();
                        } else if (myPoller.pollout(i)) {
                            ++actualCount;
                            ZeroMQConnection connection =
                                myConnections.get(myPoller.getSocket(i));
                            connection.wakeupThreadForWrite();
                            if (traceFactory.comm.getDebug()) {
                                traceFactory.comm.debugm("poll has write for " +
                                                  connection);
                            }
                            connection.doWrite();
                        }
                    }
                    if (traceFactory.comm.getDebug()) {
                        traceFactory.comm.debugm("ZMQ thread poll selects " +
                                          actualCount + "/" +
                                          selectedCount + " I/O sources");
                    }
                }
            } catch (Throwable e) {
                traceFactory.comm.errorm("polling loop failed", e);
            }
        }
    }

    /**
     * Add a socket to the set being polled.
     *
     * @param socket  The socket to add
     * @param mask  Mask indicating the kind of I/O availability of interest.
     */
    void watchSocket(ZMQ.Socket socket, int mask) {
        myPoller.register(socket, mask | ZMQ.Poller.POLLERR);
    }

    /**
     * Remove a socket from the set being polled.
     *
     * @param socket  The socket that is no longer interesting.
     */
    void unwatchSocket(ZMQ.Socket socket) {
        myPoller.unregister(socket);
    }
    
    /**
     * Interrupt the poll() call when there is work to do but no external I/O
     * availability that would otherwise cause it to return.
     */
    private void wakeup() {
        mySignalSendSocket.send(EMPTY_MESSAGE, 0);
    }

    /**
     * Make a new outbound ZeroMQ connection to another host on the net.
     *
     * @param handlerFactory  Provider of a message handler to process messages
     *    received on a new connection; since outbound ZeroMQ connections are
     *    send-only, no messages will ever be received, but the handler factory
     *    is also the callback that is notified about new connection
     *    establishment.
     * @param framerFactory  Byte I/O framer factory for the new connection
     * @param remoteAddr  Host name and port number to connect to.
     */
    void connect(final MessageHandlerFactory handlerFactory,
                 final ByteIOFramerFactory framerFactory,
                 String remoteAddr)
    {
        final boolean push;
        final String finalAddr;
        if (remoteAddr.startsWith("PUSH:")) {
            push = true;
            finalAddr = "tcp://" + remoteAddr.substring(5);
        } else if (remoteAddr.startsWith("PUB:")) {
            push = false;
            try {
                NetAddr parsedAddr = new NetAddr(remoteAddr.substring(4));
                finalAddr = "tcp://*:" + parsedAddr.getPort();
            } catch (IOException e) {
                traceFactory.comm.errorm("error setting up ZMQ connection with " +
                                  remoteAddr + ": " + e);
                return;
            }
        } else {
            push = true;
            finalAddr = "tcp://" + remoteAddr;
        }

        myQueue.enqueue((Thunk) () -> {
            traceFactory.comm.eventm("connecting ZMQ to " + finalAddr);
            ZMQ.Socket socket;
            if (push) {
                socket = myContext.socket(SocketType.PUSH);
                socket.connect(finalAddr);
            } else {
                socket = myContext.socket(SocketType.PUB);
                socket.bind(finalAddr);
            }
            ZeroMQConnection connection =
                new ZeroMQConnection(handlerFactory, framerFactory,
                                     socket, true, ZeroMQThread.this,
                                     myNetworkManager, finalAddr, clock, traceFactory);
            myConnections.put(socket, connection);
        });
        wakeup();
    }

    /**
     * Terminate an open ZeroMQ connection.
     *
     * @param connection  The connection whose closure is desired.
     */
    void close(final ZeroMQConnection connection) {
        myQueue.enqueue((Thunk) () -> {
            traceFactory.comm.eventm("closing ZMQ connection " + connection);
            ZMQ.Socket socket = connection.socket();
            unwatchSocket(socket);
            myConnections.remove(socket);
        });
        wakeup();
    }

    /**
     * Begin listening for inbound ZeroMQ connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide the handlers
     *    for connections made to this port.
     * @param framerFactory  Byte I/O framer factory for new connections.
     * @param secure  If true, use a secure connection pathway (e.g., SSL).
     *
     * @return the address that ended up being listened upon
     */
    NetAddr listen(String listenAddress,
                   final MessageHandlerFactory handlerFactory,
                   final ByteIOFramerFactory framerFactory, boolean secure)
        throws IOException
    {
        if (secure) {
            throw new Error("secure ZeroMQ not yet available");
        }

        final boolean subscribe;
        if (listenAddress.startsWith("SUB:")) {
            subscribe = true;
            listenAddress = listenAddress.substring(4);
        } else if (listenAddress.startsWith("PULL:")) {
            subscribe = false;
            listenAddress = listenAddress.substring(5);
        } else {
            subscribe = true;
        }
        NetAddr result = new NetAddr(listenAddress);
        final String finalAddress;
        if (subscribe) {
            finalAddress = "tcp://" + listenAddress;
        } else {
            finalAddress = "tcp://*:" + result.getPort();
        }

        myQueue.enqueue((Thunk) () -> {
            ZMQ.Socket socket;
            if (subscribe) {
                traceFactory.comm.eventm("subscribing to ZMQ messages from " +
                                  finalAddress);
                socket = myContext.socket(SocketType.SUB);
                socket.subscribe(UNIVERSAL_SUBSCRIPTION);
                socket.connect(finalAddress);
            } else {
                traceFactory.comm.eventm("pulling ZMQ messages at " +
                                  finalAddress);
                socket = myContext.socket(SocketType.PULL);
                socket.bind(finalAddress);
            }
            traceFactory.comm.eventm("ZMQ socket initialized");
            ZeroMQConnection connection =
                new ZeroMQConnection(handlerFactory, framerFactory,
                                     socket, false, ZeroMQThread.this,
                                     myNetworkManager, "*", clock, traceFactory);
            myConnections.put(socket, connection);
            traceFactory.comm.eventm("watching ZMQ socket");
            watchSocket(socket, ZMQ.Poller.POLLIN);
        });
        wakeup();
        return result;
    }

    /**
     * Notify this thread that a connection now has messages queued ready for
     * transmission.
     *
     * @param connection  The connection that has messages ready to send.
     */
    void readyToSend(ZeroMQConnection connection) {
        myQueue.enqueue(connection);
        wakeup();
    }
}
