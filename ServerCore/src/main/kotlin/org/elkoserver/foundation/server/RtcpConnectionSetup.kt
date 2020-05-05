package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.io.IOException

internal class RtcpConnectionSetup(label: String?, host: String, auth: AuthDesc, secure: Boolean, props: ElkoProperties, propRoot: String, myNetworkManager: NetworkManager, actorFactory: MessageHandlerFactory, trServer: Trace, tr: Trace, traceFactory: TraceFactory)
    : BaseTcpConnectionSetup(label, host, auth, secure, props, propRoot, myNetworkManager, actorFactory, trServer, tr, traceFactory) {
    override val protocol: String = "rtcp"

    @Throws(IOException::class)
    override fun createListenAddress() = myNetworkManager.listenRTCP(bind, actorFactory, msgTrace, secure)
}
