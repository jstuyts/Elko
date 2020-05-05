package org.elkoserver.foundation.net

import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.io.IOException
import java.time.Clock

/**
 * Interface implemented by classes that support message channel connectivity
 * via some I/O pathway (e.g., TCP/IP, etc.).
 *
 * The expectation, though it is not expressible by the semantics of Java's
 * 'interface' abstraction, is that classes implementing this interface will be
 * singletons; that is, one and only one instance of a given ConnectionManager
 * class will be created.
 */
interface ConnectionManager {
    /**
     * Initialize this connection manager.
     * @param networkManager  The network manager this connection manager will
     * be managing connections for.
     * @param msgTrace  Trace object for logging message traffic.
     */
    fun init(networkManager: NetworkManager, msgTrace: Trace, clock: Clock, traceFactory: TraceFactory)

    /**
     * Make a connection, using this connection manager's communications
     * modality, to another host given a host:port address.
     *
     * @param propRoot  Prefix string for all the properties describing the
     * connection that is to be made.
     * @param handlerFactory  Message handler factory to provide the handler
     * for the connection that results from this operation.
     * @param hostPort  The host name (or IP address) and port to connect to,
     * separated by a colon.  For example, "bithlo.example.com:8002".
     */
    fun connect(propRoot: String, handlerFactory: MessageHandlerFactory, hostPort: String)

    /**
     * Begin listening for incoming connections on some port, using this
     * connection manager's communications modality.
     *
     * @param propRoot  Prefix string for all the properties describing the
     * listener that is to be started.
     * @param listenAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide message
     * handlers for connections made to this port.
     * @param secure  If true, use a secure connection pathway (e.g., SSL).
     *
     * @return the address that ended up being listened upon
     *
     * @throws IOException if there was a problem establishing the listener
     */
    @Throws(IOException::class)
    fun listen(propRoot: String, listenAddress: String, handlerFactory: MessageHandlerFactory, secure: Boolean): NetAddr
}
