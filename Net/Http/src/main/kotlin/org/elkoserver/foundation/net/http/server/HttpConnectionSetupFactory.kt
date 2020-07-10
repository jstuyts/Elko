package org.elkoserver.foundation.net.http.server

import org.elkoserver.foundation.net.ConnectionSetupFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.slf4j.Gorgel

class HttpConnectionSetupFactory(
        private val props: ElkoProperties,
        private val httpServerFactory: HttpServerFactory,
        private val baseConnectionSetupGorgel: Gorgel,
        private val jsonHttpFramerCommGorgel: Gorgel,
        private val mustSendDebugReplies: Boolean) : ConnectionSetupFactory {
    override fun create(label: String?, host: String, auth: AuthDesc, secure: Boolean, propRoot: String, actorFactory: MessageHandlerFactory): HttpConnectionSetup =
            HttpConnectionSetup(label, host, auth, secure, props, propRoot, httpServerFactory, actorFactory, baseConnectionSetupGorgel, jsonHttpFramerCommGorgel, mustSendDebugReplies)
}
