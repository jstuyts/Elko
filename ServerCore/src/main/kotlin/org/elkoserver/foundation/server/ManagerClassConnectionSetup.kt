package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

internal class ManagerClassConnectionSetup(
        label: String?,
        private val mgrClass: String,
        override var serverAddress: String,
        auth: AuthDesc,
        secure: Boolean,
        props: ElkoProperties,
        propRoot: String,
        private val myNetworkManager: NetworkManager,
        private val actorFactory: MessageHandlerFactory,
        gorgel: Gorgel,
        traceFactory: TraceFactory)
    : BaseConnectionSetup(label, serverAddress, auth, secure, props, propRoot, gorgel, traceFactory) {
    override val protocol: String
        get() = "manager class: $mgrClass"

    @Throws(IOException::class)
    override fun tryToStartListener(): NetAddr {
        val result = myNetworkManager.listenVia(
                mgrClass,
                propRoot,
                bind,
                actorFactory,
                msgTrace,
                secure)
        if (serverAddress.indexOf(':') < 0) {
            serverAddress = "$serverAddress:${result.port}"
        }
        return result
    }

    override val listenAddressDescription: String
        get() = serverAddress

    override val valueToCompareWithBind: String
        get() = serverAddress

    override val connectionsSuffixForNotice: String
        get() = " using $mgrClass"

    override val protocolSuffixForErrorMessage: String
        get() = " ($mgrClass)"
}
