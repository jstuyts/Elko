package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.io.IOException

internal abstract class BaseTcpConnectionSetup(label: String?, override var serverAddress: String, auth: AuthDesc, secure: Boolean, props: ElkoProperties, propRoot: String, val myNetworkManager: NetworkManager, val actorFactory: MessageHandlerFactory, trServer: Trace, tr: Trace, traceFactory: TraceFactory)
    : BaseConnectionSetup(label, serverAddress, auth, secure, props, propRoot, trServer, tr, traceFactory) {

    @Throws(IOException::class)
    override fun tryToStartListener(): NetAddr {
        val result = createListenAddress()
        if (serverAddress.indexOf(':') < 0) {
            serverAddress = "$serverAddress:${result.port}"
        }
        return result
    }

    @Throws(IOException::class)
    abstract fun createListenAddress(): NetAddr

    override val listenAddressDescription: String
        get() = serverAddress

    override val valueToCompareWithBind: String
        get() = serverAddress
}