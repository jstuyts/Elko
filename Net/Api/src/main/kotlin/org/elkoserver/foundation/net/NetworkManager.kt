package org.elkoserver.foundation.net

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.time.Clock
import java.util.HashMap
import javax.net.ssl.SSLContext

/**
 * Manage network connections between this server and other entities.
 */
class NetworkManager(
        /** Connection count tracker.  */
        private val myConnectionCountMonitor: ConnectionCountMonitor,
        /** This server's boot properties, for initializing network access.  */
        private val myProps: ElkoProperties, loadMonitor: LoadMonitor,
        /** The run queue for this server.  */
        private val myRunner: Runner, private val timer: Timer, private val clock: Clock, private val traceFactory: TraceFactory) {

    /** System load tracker.  */
    private val myLoadMonitor: LoadMonitor

    /** Initialized SSL context, if supporting SSL, else null.  */
    private var mySSLContext: SSLContext? = null

    /** Select thread for non-blocking I/O.  */
    private var mySelectThread: SelectThread? = null

    /** Connection managers, indexed by class name.  */
    private val myConnectionManagers: MutableMap<String, ConnectionManager?>

    /**
     * Keep track of the number of connections.
     *
     * @param adjust  Adjustment being made to the number of active
     * connections, plus or minus.
     */
    fun connectionCount(adjust: Int) {
        myConnectionCountMonitor.connectionCountChange(adjust)
    }

    /**
     * Obtain the connection manager associated with a particular connection
     * manager class, either by retrieving it or creating it as needed.
     *
     * @param className  Fully qualified class name of the
     * connection manager class desired.
     * @param msgTrace  Trace object for logging message traffic.
     *
     * @return the connection manager with the given class name, or null if no
     * such connection manager is available.
     */
    private fun connectionManager(className: String, msgTrace: Trace): ConnectionManager? {
        var result = myConnectionManagers[className]
        if (result == null) {
            try {
                result = Class.forName(className).getConstructor().newInstance() as ConnectionManager
                result.init(this, msgTrace, clock, traceFactory)
                myConnectionManagers[className] = result
            } catch (e: ClassNotFoundException) {
                traceFactory.comm.errorm("ConnectionManager class $className not found: $e")
            } catch (e: InstantiationException) {
                traceFactory.comm.errorm("ConnectionManager class $className not instantiable: $e")
            } catch (e: IllegalAccessException) {
                traceFactory.comm.errorm("ConnectionManager class $className constructor not accessible: $e")
            } catch (e: NoSuchMethodException) {
                traceFactory.comm.errorm("ConnectionManager class $className does not have a public no-arg constructor: $e")
            } catch (e: InvocationTargetException) {
                traceFactory.comm.errorm("Error occurred during creation of connectionManager class $className: ${e.cause}")
            }
        }
        return result
    }

    /**
     * Make a TCP connection to another host given a host:port address.
     *
     * @param hostPort  The host name (or IP address) and port to connect to,
     * separated by a colon.  For example, "bithlo.example.com:8002".
     * @param handlerFactory  Message handler factory to provide the handler
     * for the connection that results from this operation.
     * @param framerFactory  Byte I/O framer factory for the new connection.
     * @param trace  Trace object to use for activity on the new connection.
     */
    fun connectTCP(hostPort: String,
                   handlerFactory: MessageHandlerFactory,
                   framerFactory: ByteIOFramerFactory?, trace: Trace?) {
        connectionCount(1)
        ensureSelectThread()
        mySelectThread!!.connect(handlerFactory, framerFactory, hostPort, trace)
    }

    /**
     * Make a connection to another host given a host:port address using a
     * named connection manager class.
     *
     * @param connectionManagerClassName  Fully qualified class name of the
     * connection manager class to use to make this connection.
     * @param propRoot  Prefix string for all the properties describing the
     * connection that is to be made.
     * @param hostPort  The host name (or IP address) and port to connect to,
     * separated by a colon.  For example, "bithlo.example.com:8002".
     * @param handlerFactory  Message handler factory to provide the handler
     * for the connection that results from this operation.
     * @param msgTrace  Trace object for logging message traffic.
     */
    fun connectVia(connectionManagerClassName: String,
                   propRoot: String,
                   hostPort: String,
                   handlerFactory: MessageHandlerFactory,
                   msgTrace: Trace) {
        val connMgr = connectionManager(connectionManagerClassName, msgTrace)
        if (connMgr == null) {
            handlerFactory.provideMessageHandler(null)
        } else {
            connectionCount(1)
            connMgr.connect(propRoot, handlerFactory, hostPort)
        }
    }

    /**
     * Start the select thread if it's not already running.
     */
    private fun ensureSelectThread() {
        if (mySelectThread == null) {
            mySelectThread = SelectThread(this, mySSLContext, clock, traceFactory)
        }
    }

    /**
     * Begin listening for incoming HTTP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     * handlers for messages passed inside HTTP requests on connections made
     * to this port.
     * @param trace  Trace object to use for activity on this connection.
     *
     * @param secure  If true, use SSL.
     * @param rootURI  The root URI that GETs and POSTs must reference.
     * @param httpFramer  HTTP framer to interpret HTTP POSTs and format HTTP
     * replies.
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenHTTP(listenAddress: String?,
                   innerHandlerFactory: MessageHandlerFactory,
                   trace: Trace?, secure: Boolean, rootURI: String, httpFramer: HTTPFramer): NetAddr {
        val outerHandlerFactory: MessageHandlerFactory = HTTPMessageHandlerFactory(
                innerHandlerFactory, rootURI, httpFramer, this, timer, clock, traceFactory)
        val framerFactory: ByteIOFramerFactory = HTTPRequestByteIOFramerFactory(traceFactory)
        return listenTCP(listenAddress, outerHandlerFactory, trace, secure, framerFactory)
    }

    /**
     * Begin listening for incoming RTCP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     * handlers for messages passed inside RTCP requests on connections made
     * to this port.
     * @param msgTrace   Trace object for logging message traffic
     * @param secure  If true, use SSL.
     *
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenRTCP(listenAddress: String?,
                   innerHandlerFactory: MessageHandlerFactory,
                   msgTrace: Trace,
                   secure: Boolean): NetAddr {
        val outerHandlerFactory: MessageHandlerFactory = RTCPMessageHandlerFactory(innerHandlerFactory, msgTrace, this, timer, clock, traceFactory)
        val framerFactory: ByteIOFramerFactory = RTCPRequestByteIOFramerFactory(msgTrace, traceFactory)
        return listenTCP(listenAddress, outerHandlerFactory, msgTrace, secure, framerFactory)
    }

    /**
     * Begin listening for incoming WebSocket connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param innerHandlerFactory  Message handler factory to provide message
     * handlers for messages passed inside WebSocket frames on connections
     * made to this port.
     * @param msgTrace   Trace object for logging message traffic
     * @param secure  If true, use SSL.
     *
     * @param socketURI  The WebSocket URI that browsers connect to
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenWebSocket(listenAddress: String,
                        innerHandlerFactory: MessageHandlerFactory,
                        msgTrace: Trace, secure: Boolean, socketURI: String): NetAddr {
        var actualSocketURI = socketURI
        if (!actualSocketURI.startsWith("/")) {
            actualSocketURI = "/$actualSocketURI"
        }
        val outerHandlerFactory: MessageHandlerFactory = WebSocketMessageHandlerFactory(innerHandlerFactory, actualSocketURI,
                msgTrace)
        val framerFactory: ByteIOFramerFactory = WebSocketByteIOFramerFactory(msgTrace, listenAddress, actualSocketURI, traceFactory)
        return listenTCP(listenAddress, outerHandlerFactory, msgTrace, secure, framerFactory)
    }

    /**
     * Begin listening for incoming TCP connections on some port.
     *
     * @param listenAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide message
     * handlers for connections made to this port.
     * @param portTrace  Trace object for logging activity associated with this
     * port &amp; its connections
     *
     * @param secure  If true, use SSL.
     * @param framerFactory  Byte I/O framer factory for the new connection.
     * @return the address that ended up being listened upon
     */
    @Throws(IOException::class)
    fun listenTCP(listenAddress: String?,
                  handlerFactory: MessageHandlerFactory?,
                  portTrace: Trace?, secure: Boolean, framerFactory: ByteIOFramerFactory?): NetAddr {
        ensureSelectThread()
        val listener = mySelectThread!!.listen(listenAddress, handlerFactory,
                framerFactory, secure, portTrace)
        return listener.listenAddress()
    }

    /**
     * Begin listening for incoming connections on some port using a named
     * connection manager class.
     *
     * @param connectionManagerClassName  Fully qualified class name of the
     * connection manager class to use to make this connection.
     * @param propRoot  Prefix string for all the properties describing the
     * listener that is to be started.
     * @param listenAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide message
     * handlers for connections made to this port.
     * @param msgTrace  Trace object for logging message traffic.
     * @param secure  If true, use a secure connection pathway (e.g., SSL).
     *
     * @return the address that ended up being listened upon
     *
     * @throws IOException if the requested connection manager was unavailable.
     */
    @Throws(IOException::class)
    fun listenVia(connectionManagerClassName: String,
                  propRoot: String,
                  listenAddress: String,
                  handlerFactory: MessageHandlerFactory,
                  msgTrace: Trace,
                  secure: Boolean): NetAddr? {
        val connMgr = connectionManager(connectionManagerClassName, msgTrace)
                ?: throw IOException("no connection manager $connectionManagerClassName")
        return connMgr.listen(propRoot, listenAddress, handlerFactory, secure)
    }

    /**
     * Get the load monitor for this server.
     *
     * @return the load monitor.
     */
    fun loadMonitor(): LoadMonitor {
        return myLoadMonitor
    }

    /**
     * Get the run queue for this server.
     *
     * @return the current runner.
     */
    fun runner(): Runner {
        return myRunner
    }

    /**
     * Get this server's properties.
     *
     * @return the properties
     */
    fun props(): ElkoProperties {
        return myProps
    }

    /**
     * Do all the various key and certificate management stuff needed to set
     * up to support SSL connections.
     */
    private fun setupSSL() {
        mySSLContext = SslSetup.setupSsl(myProps, "conf.ssl.", traceFactory.startup)
    }

    /**
     * Obtain the SSL context for SSL connections, if there is one.
     *
     * @return the SSL context, if supporting SSL, else null.
     */
    fun sslContext(): SSLContext? {
        return mySSLContext
    }

    /**
     * Construct a NetworkManager for this server.
     *
     * @param connectionCountMonitor  Monitor for tracking session count.
     * @param props  Boot properties for this server.
     * @param loadMonitor  Load monitor for tracking system load.
     * @param runner  The Runner managing this server's run queue.
     */
    init {
        myConnectionManagers = HashMap()
        // FIXME: Initialize somewhere else
        HTTPSessionConnection.initializeRNG()
        RTCPSessionConnection.initializeRNG()
        myLoadMonitor = loadMonitor
        if (myProps.testProperty("conf.ssl.enable")) {
            setupSSL()
        }
    }
}