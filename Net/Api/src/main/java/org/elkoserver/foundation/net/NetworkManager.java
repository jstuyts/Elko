package org.elkoserver.foundation.net;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLContext;
import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.foundation.run.Runner;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.util.trace.Trace;

/**
 * Manage network connections between this server and other entities.
 */
public class NetworkManager {
    /** Connection count tracker. */
    private ConnectionCountMonitor myConnectionCountMonitor;

    /** This server's boot properties, for initializing network access. */
    private BootProperties myProps;

    /** The run queue for this server. */
    private Runner myRunner;

    /** System load tracker. */
    private LoadMonitor myLoadMonitor;

    /** Initialized SSL context, if supporting SSL, else null. */
    private SSLContext mySSLContext;

    /** Select thread for non-blocking I/O. */
    private SelectThread mySelectThread;

    /** Connection managers, indexed by class name. */
    private Map<String, ConnectionManager> myConnectionManagers;

    /**
     * Construct a NetworkManager for this server.
     *
     * @param connectionCountMonitor  Monitor for tracking session count.
     * @param props  Boot properties for this server.
     * @param loadMonitor  Load monitor for tracking system load.
     * @param runner  The Runner managing this server's run queue.
     */
    public NetworkManager(ConnectionCountMonitor connectionCountMonitor,
                          BootProperties props, LoadMonitor loadMonitor,
                          Runner runner)
    {
        myConnectionCountMonitor = connectionCountMonitor;
        myProps = props;
        myRunner = runner;
        myConnectionManagers = new HashMap<>();
        // FIXME: Initialize somewhere else
        HTTPSessionConnection.initializeRNG();
        RTCPSessionConnection.initializeRNG();
        myLoadMonitor = loadMonitor;

        if (props.testProperty("conf.ssl.enable")) {
            setupSSL();
        }
    }

    /**
     * Keep track of the number of connections.
     *
     * @param adjust  Adjustment being made to the number of active
     *    connections, plus or minus.
     */
    public void connectionCount(int adjust) {
        myConnectionCountMonitor.connectionCountChange(adjust);
    }

    /**
     * Obtain the connection manager associated with a particular connection
     * manager class, either by retrieving it or creating it as needed.
     *
     * @param className  Fully qualified class name of the
     *    connection manager class desired.
     * @param msgTrace  Trace object for logging message traffic.
     *
     * @return the connection manager with the given class name, or null if no
     *    such connection manager is available.
     */
    private ConnectionManager connectionManager(String className, Trace msgTrace)
    {
        ConnectionManager result = myConnectionManagers.get(className);
        if (result == null) {
            try {
                result =
                    (ConnectionManager) Class.forName(className).newInstance();
                result.init(this, msgTrace);
                myConnectionManagers.put(className, result);
            } catch (ClassNotFoundException e) {
                Trace.comm.errorm("ConnectionManager class " + className +
                                  " not found: " + e);
            } catch (InstantiationException e) {
                Trace.comm.errorm("ConnectionManager class " + className +
                                  " not instantiable: " + e);
            } catch (IllegalAccessException e) {
                Trace.comm.errorm("ConnectionManager class " + className +
                                  " constructor not accessible: " + e);
            }
        }
        return result;
    }

    /**
     * Make a TCP connection to another host given a host:port address.
     *
     * @param hostPort  The host name (or IP address) and port to connect to,
     *    separated by a colon.  For example, "bithlo.example.com:8002".
     * @param handlerFactory  Message handler factory to provide the handler
     *    for the connection that results from this operation.
     * @param framerFactory  Byte I/O framer factory for the new connection.
     * @param trace  Trace object to use for activity on the new connection.
     */
    public void connectTCP(String hostPort,
                           MessageHandlerFactory handlerFactory,
                           ByteIOFramerFactory framerFactory, Trace trace)
    {
        connectionCount(1);
        ensureSelectThread();
        mySelectThread.connect(handlerFactory, framerFactory, hostPort, trace);
    }

    /**
     * Make a connection to another host given a host:port address using a
     * named connection manager class.
     *
     * @param connectionManagerClassName  Fully qualified class name of the
     *    connection manager class to use to make this connection.
     * @param propRoot  Prefix string for all the properties describing the
     *    connection that is to be made.
     * @param hostPort  The host name (or IP address) and port to connect to,
     *    separated by a colon.  For example, "bithlo.example.com:8002".
     * @param handlerFactory  Message handler factory to provide the handler
     *    for the connection that results from this operation.
     * @param msgTrace  Trace object for logging message traffic.
     */
    public void connectVia(String connectionManagerClassName,
                           String propRoot,
                           String hostPort,
                           MessageHandlerFactory handlerFactory,
                           Trace msgTrace)
    {
        ConnectionManager connMgr =
            connectionManager(connectionManagerClassName, msgTrace);
        if (connMgr == null) {
            handlerFactory.provideMessageHandler(null);
        } else {
            connectionCount(1);
            connMgr.connect(propRoot, handlerFactory, hostPort);
        }
    }

    /**
     * Start the select thread if it's not already running.
     */
    private void ensureSelectThread() {
        if (mySelectThread == null) {
            mySelectThread = new SelectThread(this, mySSLContext);
        }
    }

    /**
     * Begin listening for incoming HTTP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     *    handlers for messages passed inside HTTP requests on connections made
     *    to this port.
     * @param trace  Trace object to use for activity on this connection.
     *
     * @param secure  If true, use SSL.
     * @param rootURI  The root URI that GETs and POSTs must reference.
     * @param httpFramer  HTTP framer to interpret HTTP POSTs and format HTTP
     *    replies.
     * @return the address that ended up being listened upon
     */
    public NetAddr listenHTTP(String listenAddress,
                              MessageHandlerFactory innerHandlerFactory,
                              Trace trace, boolean secure, String rootURI, HTTPFramer httpFramer)
        throws IOException
    {
        MessageHandlerFactory outerHandlerFactory =
            new HTTPMessageHandlerFactory(
               innerHandlerFactory, rootURI, httpFramer, this);

        ByteIOFramerFactory framerFactory =
            new HTTPRequestByteIOFramerFactory();

        return listenTCP(listenAddress, outerHandlerFactory, trace, secure, framerFactory);
    }

    /**
     * Begin listening for incoming RTCP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     *    handlers for messages passed inside RTCP requests on connections made
     *    to this port.
     * @param msgTrace   Trace object for logging message traffice
     * @param secure  If true, use SSL.
     *
     * @return the address that ended up being listened upon
     */
    public NetAddr listenRTCP(String listenAddress,
                              MessageHandlerFactory innerHandlerFactory,
                              Trace msgTrace,
                              boolean secure)
        throws IOException
    {
        MessageHandlerFactory outerHandlerFactory =
            new RTCPMessageHandlerFactory(innerHandlerFactory, msgTrace, this);

        ByteIOFramerFactory framerFactory =
            new RTCPRequestByteIOFramerFactory(msgTrace);

        return listenTCP(listenAddress, outerHandlerFactory, msgTrace, secure, framerFactory);
    }

    /**
     * Begin listening for incoming WebSocket connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     *    handlers for messages passed inside WebSocket frames on connections
     *    made to this port.
     * @param msgTrace   Trace object for logging message traffice
     * @param secure  If true, use SSL.
     *
     * @param socketURI  The WebSocket URI that browsers connect to
     * @return the address that ended up being listened upon
     */
    public NetAddr listenWebSocket(String listenAddress,
                                   MessageHandlerFactory innerHandlerFactory,
                                   Trace msgTrace, boolean secure, String socketURI)
        throws IOException
    {
        if (!socketURI.startsWith("/")) {
            socketURI = "/" + socketURI;
        }
        MessageHandlerFactory outerHandlerFactory =
            new WebSocketMessageHandlerFactory(innerHandlerFactory, socketURI,
                                               msgTrace, this);

        ByteIOFramerFactory framerFactory =
            new WebSocketByteIOFramerFactory(msgTrace, listenAddress, socketURI);

        return listenTCP(listenAddress, outerHandlerFactory, msgTrace, secure, framerFactory);
    }

    /**
     * Begin listening for incoming TCP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide message
     *    handlers for connections made to this port.
     * @param portTrace  Trace object for logging activity associated with this
     *   port &amp; its connections
     *
     * @param secure  If true, use SSL.
     * @param framerFactory  Byte I/O framer factory for the new connection.
     * @return the address that ended up being listened upon
     */
    public NetAddr listenTCP(String listenAddress,
                             MessageHandlerFactory handlerFactory,
                             Trace portTrace, boolean secure, ByteIOFramerFactory framerFactory)
        throws IOException
    {
        ensureSelectThread();
        Listener listener =
            mySelectThread.listen(listenAddress, handlerFactory,
                                  framerFactory, secure, portTrace);
        return listener.listenAddress();
    }

    /**
     * Begin listening for incoming connections on some port using a named
     * connection manager class.
     *
     * @param connectionManagerClassName  Fully qualified class name of the
     *    connection manager class to use to make this connection.
     * @param propRoot  Prefix string for all the properties describing the
     *    listener that is to be started.
     * @param listenAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide message
     *    handlers for connections made to this port.
     * @param msgTrace  Trace object for logging message traffic.
     * @param secure  If true, use a secure connection pathway (e.g., SSL).
     *
     * @return the address that ended up being listened upon
     *
     * @throws IOException if the requested connection manager was unavailable.
     */
    public NetAddr listenVia(String connectionManagerClassName,
                             String propRoot,
                             String listenAddress,
                             MessageHandlerFactory handlerFactory,
                             Trace msgTrace,
                             boolean secure)
        throws IOException
    {
        ConnectionManager connMgr =
            connectionManager(connectionManagerClassName, msgTrace);
        if (connMgr == null) {
            throw new IOException("no connection manager " +
                                  connectionManagerClassName);
        }
        return connMgr.listen(propRoot, listenAddress, handlerFactory, secure);
    }

    /**
     * Get the load monitor for this server.
     *
     * @return the load monitor.
     */
    LoadMonitor loadMonitor() {
        return myLoadMonitor;
    }

    /**
     * Get the run queue for this server.
     *
     * @return the current runner.
     */
    Runner runner() {
        return myRunner;
    }

    /**
     * Get this server's properties.
     *
     * @return the properties
     */
    public BootProperties props() {
        return myProps;
    }

    /**
     * Do all the various key and certificate management stuff needed to set
     * up to support SSL connections.
     */
    private void setupSSL() {
        mySSLContext = SslSetup.setupSsl(this.myProps, "conf.ssl.", Trace.startup);
    }

    /**
     * Obtain the SSL context for SSL connections, if there is one.
     *
     * @return the SSL context, if supporting SSL, else null.
     */
    SSLContext sslContext() {
        return mySSLContext;
    }
}
