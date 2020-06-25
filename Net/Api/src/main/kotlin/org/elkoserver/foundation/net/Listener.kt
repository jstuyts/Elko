package org.elkoserver.foundation.net

import org.elkoserver.foundation.byteioframer.ByteIOFramerFactory
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel

/**
 * A listener for new inbound TCP connections to some server port.
 *
 * This must *not* be called from inside the select thread.
 *
 * @param myLocalAddress  The local address and port to will listen on.
 * @param myHandlerFactory  Message handler factory to provide the handlers
 * for connections made to this port.
 * @param myFramerFactory  Byte I/O framer factory for new connections.
 * @param amSecure  If true, use SSL.
 * port & its connections
 */
class Listener(
        private val myLocalAddress: String,
        private val myHandlerFactory: MessageHandlerFactory,
        private val myFramerFactory: ByteIOFramerFactory,
        private val amSecure: Boolean,
        private val myGorgel: Gorgel,
        private val tcpConnectionTrace: Trace) {
    /** The address to listen on, or null for the default address.  */
    private val myOptIP: InetAddress

    /** The channel doing the actual listening.  */
    private val myChannel: ServerSocketChannel

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
            val newChannel = myChannel.accept()
            if (newChannel != null) {
                mySelectThread!!.newChannel(myHandlerFactory, myFramerFactory, newChannel, amSecure, tcpConnectionTrace)
            } else {
                myGorgel.i?.run { info("accept returned null socket, ignoring") }
            }
        } catch (e: IOException) {
            myGorgel.warn("accept on $myLocalAddress failed -- closing listener, IOException: ${e.message}")
            try {
                myChannel.close()
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
    fun listenAddress(): NetAddr = NetAddr(myOptIP, myChannel.socket().localPort)

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
        val key = myChannel.register(selector, SelectionKey.OP_ACCEPT)
        key.attach(this)
    }

    init {
        val netAddr = NetAddr(myLocalAddress)
        myOptIP = netAddr.inetAddress!!
        val localPort = netAddr.port
        if (false && amSecure) {
//            val asSSL = SSLServerSocketChannel.open(myMgr.sslContext)
//            myChannel = asSSL
//            asSSL.socket().needClientAuth = false
            throw IllegalStateException()
        } else {
            myChannel = ServerSocketChannel.open()
        }
        myChannel.configureBlocking(false)
        myChannel.socket().bind(InetSocketAddress(myOptIP, localPort), 50)
    }
}
