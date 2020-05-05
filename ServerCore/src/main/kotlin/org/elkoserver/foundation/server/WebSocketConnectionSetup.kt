package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.io.IOException

internal class WebSocketConnectionSetup(label: String?, host: String, auth: AuthDesc, secure: Boolean, props: ElkoProperties, propRoot: String, private val myNetworkManager: NetworkManager, private val actorFactory: MessageHandlerFactory, trServer: Trace, tr: Trace, traceFactory: TraceFactory)
    : BaseConnectionSetup(label, host, auth, secure, props, propRoot, trServer, tr, traceFactory) {
    private val socketURI: String = props.getProperty("$propRoot.sock", "")
    override val serverAddress: String
    override val protocol: String = "ws"

    @Throws(IOException::class)
    override fun tryToStartListener(): NetAddr {
        return myNetworkManager.listenWebSocket(
                bind,
                actorFactory,
                msgTrace, secure, socketURI)
    }

    init {
        val socketURI = props.getProperty("$propRoot.sock", "")
        serverAddress = "$host/$socketURI"
    }

    override val listenAddressDescription: String
        get() = "$host/$socketURI"

    override val valueToCompareWithBind: String
        get() = host
}
