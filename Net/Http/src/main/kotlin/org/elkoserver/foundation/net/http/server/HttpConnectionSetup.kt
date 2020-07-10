package org.elkoserver.foundation.net.http.server

import org.elkoserver.foundation.net.BaseConnectionSetup
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

class HttpConnectionSetup(
        label: String?,
        host: String,
        auth: AuthDesc,
        secure: Boolean,
        props: ElkoProperties,
        propRoot: String,
        private val httpServerFactory: HttpServerFactory,
        private val actorFactory: MessageHandlerFactory,
        gorgel: Gorgel,
        private val jsonHttpFramerCommGorgel: Gorgel,
        private val mustSendDebugReplies: Boolean) : BaseConnectionSetup(label, host, auth, secure, props, propRoot, gorgel) {
    private val domain = determineDomain(host, props, propRoot)
    private val rootUri = props.getProperty("$propRoot.root", "")
    override val serverAddress = "$host/$rootUri"

    override val protocol: String = "http"

    @Throws(IOException::class)
    override fun tryToStartListener(): NetAddr =
            httpServerFactory.listenHTTP(bind, actorFactory, secure, rootUri, JsonHttpFramer(jsonHttpFramerCommGorgel, mustSendDebugReplies))

    override val listenAddressDescription = "$host/$rootUri/ in domain $domain"

    override val valueToCompareWithBind = host

    companion object {
        private fun determineDomain(host: String, props: ElkoProperties, propRoot: String): String? {
            var result = props.getProperty("$propRoot.domain")
            if (result == null) {
                val colonIndex = host.indexOf(':')
                result = if (colonIndex == -1) {
                    host
                } else {
                    host.take(colonIndex)
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
