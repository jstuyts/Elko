package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

class HttpConnectionSetupFactory(
        private val props: ElkoProperties,
        private val networkManager: NetworkManager,
        private val baseConnectionSetupGorgel: Gorgel,
        private val listenerGorgel: Gorgel,
        private val jsonHttpFramerCommGorgel: Gorgel,
        private val traceFactory: TraceFactory,
        private val mustSendDebugReplies: Boolean) : ConnectionSetupFactory {
    override fun create(label: String?, host: String, auth: AuthDesc, secure: Boolean, propRoot: String, actorFactory: MessageHandlerFactory) =
            HttpConnectionSetup(label, host, auth, secure, props, propRoot, networkManager, actorFactory, baseConnectionSetupGorgel, listenerGorgel, jsonHttpFramerCommGorgel, traceFactory, mustSendDebugReplies)
}
