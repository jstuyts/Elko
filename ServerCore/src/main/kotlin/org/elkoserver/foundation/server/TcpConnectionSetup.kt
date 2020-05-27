package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.JSONByteIOFramerFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

internal class TcpConnectionSetup(label: String?, host: String, auth: AuthDesc, secure: Boolean, props: ElkoProperties, propRoot: String, myNetworkManager: NetworkManager, actorFactory: MessageHandlerFactory, gorgel: Gorgel, traceFactory: TraceFactory)
    : BaseTcpConnectionSetup(label, host, auth, secure, props, propRoot, myNetworkManager, actorFactory, gorgel, traceFactory) {
    override val protocol = "tcp"

    @Throws(IOException::class)
    override fun createListenAddress() =
            myNetworkManager.listenTCP(bind, actorFactory, msgTrace, secure, JSONByteIOFramerFactory(msgTrace, traceFactory))
}
