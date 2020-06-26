package org.elkoserver.foundation.net.rtcp.server

import org.elkoserver.foundation.net.BaseConnectionSetup
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

class RtcpConnectionSetup(
        label: String?,
        override var serverAddress: String,
        auth: AuthDesc,
        secure: Boolean,
        props: ElkoProperties,
        propRoot: String,
        private val rtcpServerFactory: RtcpServerFactory,
        private val actorFactory: MessageHandlerFactory,
        gorgel: Gorgel,
        traceFactory: TraceFactory)
    : BaseConnectionSetup(label, serverAddress, auth, secure, props, propRoot, gorgel, traceFactory) {
    override val protocol: String = "rtcp"

    @Throws(IOException::class)
    override fun tryToStartListener(): NetAddr {
        val result = createListenAddress()
        if (serverAddress.indexOf(':') < 0) {
            serverAddress = "$serverAddress:${result.port}"
        }
        return result
    }

    override val listenAddressDescription: String
        get() = serverAddress

    override val valueToCompareWithBind: String
        get() = serverAddress

    @Throws(IOException::class)
    private fun createListenAddress() = rtcpServerFactory.listenRTCP(bind, actorFactory, secure, msgTrace)
}
