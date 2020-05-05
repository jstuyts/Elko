package org.elkoserver.foundation.net

import org.elkoserver.util.trace.Trace
import scalablessl.SSLServerSocketChannel
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel

/**
 * A listener for new inbound TCP connections to some server port.
 */
internal class Listener(
        /** Address string for the requested listen address.  */
        private val myLocalAddress: String,
        /** The message handler factory for connections to this listener's port.  */
        private val myHandlerFactory: MessageHandlerFactory,
        /** Low-level I/O framer factory for connections to this listener's port  */
        private val myFramerFactory: ByteIOFramerFactory,
        /** Network manager for this server.  */
        private val myMgr: NetworkManager,
        /** Listener is expecting SSL connections.  */
        private val amSecure: Boolean,
        /** Trace object this listener should use.  */
        private val myTrace: Trace) {
    /** The address to listen on, or null for the default address.  */
    private val myOptIP: InetAddress

    /** The channel doing the actual listening.  */
    private var myChannel: ServerSocketChannel? = null

    /** The thread that does select calls on behalf of this listener.  */
    private var mySelectThread: SelectThread? = null

    /**
     * Do an accept() operation, given that the selector has indicated that
     * this can happen without blocking.
     *
     * This *must* be called from inside the select thread.
     */
    fun doAccept() {
        try {
            val newChannel = myChannel!!.accept()
            if (newChannel != null) {
                myMgr.connectionCount(1)
                mySelectThread!!.newChannel(myHandlerFactory, myFramerFactory,
                        newChannel, amSecure, myTrace)
            } else {
                myTrace.usagem("accept returned null socket, ignoring")
            }
        } catch (e: IOException) {
            myTrace.warningm("accept on " + myLocalAddress +
                    " failed -- closing listener, IOException: " +
                    e.message)
            try {
                myChannel!!.close()
            } catch (e2: IOException) {
                /* Yeah, like I could do something about it... */
            }
        }
    }

    /**
     * Get the address on which this listener is listening.
     *
     * @return this listener's listen address.
     */
    fun listenAddress(): NetAddr {
        return NetAddr(myOptIP, myChannel!!.socket().localPort)
    }

    /**
     * Register this listener with a selector.
     *
     * This *must* be run from inside the select thread.
     *
     * @param selectThread  The select thread that is managing this listener
     * @param selector  The selector to register with
     */
    @Throws(ClosedChannelException::class)
    fun register(selectThread: SelectThread?, selector: Selector?) {
        mySelectThread = selectThread
        val key = myChannel!!.register(selector, SelectionKey.OP_ACCEPT)
        key.attach(this)
    }

    /**
     * Constructor.
     *
     * This must *not* be called from inside the select thread.
     *
     * @param localAddress  The local address and port to will listen on.
     * @param handlerFactory  Message handler factory to provide the handlers
     * for connections made to this port.
     * @param framerFactory  Byte I/O framer factory for new connections.
     * @param mgr  Network manager for this server.
     * @param secure  If true, use SSL.
     * @param portTrace  Trace object for logging activity associated with this
     * port & its connections
     */
    init {
        val netAddr = NetAddr(myLocalAddress)
        myOptIP = netAddr.inetAddress()!!
        val localPort = netAddr.port
        val asSSL: SSLServerSocketChannel
        if (amSecure) {
            asSSL = SSLServerSocketChannel.open(myMgr.sslContext())
            myChannel = asSSL
            asSSL.socket().needClientAuth = false
        } else {
            myChannel = ServerSocketChannel.open()
        }
        myChannel!!.configureBlocking(false)
        myChannel!!.socket().bind(InetSocketAddress(myOptIP, localPort), 50)
    }
}
