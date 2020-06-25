package org.elkoserver.foundation.net.ws.server

import org.elkoserver.foundation.net.ConnectionSetupFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

class WebSocketConnectionSetupFactory(
        private val props: ElkoProperties,
        private val webSocketServerFactory: WebSocketServerFactory,
        private val baseConnectionSetupGorgel: Gorgel,
        private val listenerGorgel: Gorgel,
        private val traceFactory: TraceFactory) : ConnectionSetupFactory {
    override fun create(label: String?, host: String, auth: AuthDesc, secure: Boolean, propRoot: String, actorFactory: MessageHandlerFactory) =
            WebSocketConnectionSetup(label, host, auth, secure, props, propRoot, webSocketServerFactory, actorFactory, baseConnectionSetupGorgel, listenerGorgel, traceFactory)
}
