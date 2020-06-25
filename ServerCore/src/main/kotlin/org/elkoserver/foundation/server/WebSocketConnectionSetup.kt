package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.ws.server.WebSocketServerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

class WebSocketConnectionSetup(
        label: String?,
        host: String,
        auth: AuthDesc,
        secure: Boolean,
        props: ElkoProperties,
        propRoot: String,
        private val webSocketServerFactory: WebSocketServerFactory,
        private val actorFactory: MessageHandlerFactory,
        gorgel: Gorgel,
        listenerGorgel: Gorgel,
        traceFactory: TraceFactory)
    : BaseConnectionSetup(label, host, auth, secure, props, propRoot, gorgel, listenerGorgel, traceFactory) {
    private val socketURI: String = props.getProperty("$propRoot.sock", "")
    override val serverAddress: String
    override val protocol: String = "ws"

    @Throws(IOException::class)
    override fun tryToStartListener() =
            webSocketServerFactory.listenWebSocket(bind, actorFactory, secure, socketURI, msgTrace)

    init {
        val socketURI = props.getProperty("$propRoot.sock", "")
        serverAddress = "$host/$socketURI"
    }

    override val listenAddressDescription: String
        get() = "$host/$socketURI"

    override val valueToCompareWithBind: String
        get() = host
}
