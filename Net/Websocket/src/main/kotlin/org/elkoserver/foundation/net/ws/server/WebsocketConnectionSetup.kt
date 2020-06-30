package org.elkoserver.foundation.net.ws.server

import org.elkoserver.foundation.net.BaseConnectionSetup
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

class WebsocketConnectionSetup(
        label: String?,
        host: String,
        auth: AuthDesc,
        secure: Boolean,
        props: ElkoProperties,
        propRoot: String,
        private val websocketServerFactory: WebsocketServerFactory,
        private val actorFactory: MessageHandlerFactory,
        gorgel: Gorgel)
    : BaseConnectionSetup(label, host, auth, secure, props, propRoot, gorgel) {
    private val socketURI: String = props.getProperty("$propRoot.sock", "")
    override val serverAddress: String
    override val protocol: String = "ws"

    @Throws(IOException::class)
    override fun tryToStartListener() =
            websocketServerFactory.listenWebsocket(bind, actorFactory, secure, socketURI, actualGorgel)

    init {
        val socketURI = props.getProperty("$propRoot.sock", "")
        serverAddress = "$host/$socketURI"
    }

    override val listenAddressDescription: String
        get() = "$host/$socketURI"

    override val valueToCompareWithBind: String
        get() = host
}
