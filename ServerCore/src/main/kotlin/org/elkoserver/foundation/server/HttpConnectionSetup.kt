package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.JSONHTTPFramer
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.io.IOException

internal class HttpConnectionSetup(label: String?, host: String, auth: AuthDesc?, secure: Boolean, props: ElkoProperties, propRoot: String, private val myNetworkManager: NetworkManager, private val actorFactory: MessageHandlerFactory, trServer: Trace?, tr: Trace?, traceFactory: TraceFactory?) : BaseConnectionSetup(label, host, auth!!, secure, props, propRoot, trServer!!, tr!!, traceFactory!!) {
    private val domain: String?
    private val rootURI: String
    override val serverAddress: String

    override val protocol = "http"

    @Throws(IOException::class)
    override fun tryToStartListener() =
            myNetworkManager.listenHTTP(bind, actorFactory, msgTrace, secure, rootURI, JSONHTTPFramer(msgTrace, traceFactory))

    override val listenAddressDescription: String
        get() = "$host/$rootURI/ in domain $domain"

    override val valueToCompareWithBind: String
        get() = host

    init {
        domain = determineDomain(host, props, propRoot)
        rootURI = props.getProperty("$propRoot.root", "")
        serverAddress = "$host/$rootURI"
    }

    companion object {
        private fun determineDomain(host: String, props: ElkoProperties, propRoot: String): String? {
            var result = props.getProperty("$propRoot.domain")
            if (result == null) {
                val colonIndex = host.indexOf(':')
                result = if (colonIndex == -1) {
                    host
                } else {
                    host.substring(0, colonIndex)
                }
                var indexOfLastDot = result.lastIndexOf('.')
                indexOfLastDot = result.lastIndexOf('.', indexOfLastDot - 1)
                if (indexOfLastDot > 0) {
                    result = result.substring(indexOfLastDot + 1)
                }
            }
            return result
        }
    }
}