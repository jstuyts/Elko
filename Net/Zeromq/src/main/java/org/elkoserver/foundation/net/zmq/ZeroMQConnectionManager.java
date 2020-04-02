package org.elkoserver.foundation.net.zmq;

import org.elkoserver.foundation.net.ByteIOFramerFactory;
import org.elkoserver.foundation.net.ConnectionManager;
import org.elkoserver.foundation.net.JSONByteIOFramerFactory;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NetAddr;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

import java.io.IOException;
import java.time.Clock;

/**
 * Connection manager for connections using the ZeroMQ queued messaging
 * library.
 */
public class ZeroMQConnectionManager implements ConnectionManager {
    private ZeroMQThread myZeroMQThread;
    private Trace myMsgTrace;
    private TraceFactory traceFactory;
    private Clock clock;

    /**
     * Initialize this connection manager.
     *
     * @param networkManager  The network manager this connection manager will
     *    be managing connections for.
     * @param clock
     */
    public void init(NetworkManager networkManager, Trace msgTrace, Clock clock, TraceFactory traceFactory) {
        this.traceFactory = traceFactory;
        myZeroMQThread = new ZeroMQThread(networkManager, traceFactory, this.clock);
        myMsgTrace = msgTrace;
    }

    /**
     * Make a connection, using this connection manager's communications
     * modality, to another host given a host:port address.
     *
     * @param propRoot  Prefix string for all the properties describing the
     *    connection that is to be made.
     * @param handlerFactory  Message handler factory to provide the handler
     *    for the connection that results from this operation.
     * @param hostPort  The host name (or IP address) and port to connect to,
     *    separated by a colon.  For example, "bithlo.example.com:8002".
     */
    public void connect(String propRoot, MessageHandlerFactory handlerFactory,
                        String hostPort)
    {
        ByteIOFramerFactory framerFactory =
            new JSONByteIOFramerFactory(myMsgTrace, traceFactory);
        myZeroMQThread.connect(handlerFactory, framerFactory, hostPort);
    }

    /**
     * Begin listening for incoming connections on some port, using this
     * connection manager's communications modality.
     *
     * @param propRoot  Prefix string for all the properties describing the
     *    listener that is to be started.
     * @param listenAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide message
     *    handlers for connections made to this port.
     * @param secure  If true, use a secure connection pathway (e.g., SSL).
     *
     * @return the address that ended up being listened upon
     *
     * @throws IOException if there was a problem establishing the listener
     */
    public NetAddr listen(String propRoot, String listenAddress,
                          MessageHandlerFactory handlerFactory, boolean secure)
        throws IOException
    {
        ByteIOFramerFactory framerFactory =
            new JSONByteIOFramerFactory(myMsgTrace, traceFactory);
        return myZeroMQThread.listen(listenAddress, handlerFactory,
                                     framerFactory, secure);
    }
}
