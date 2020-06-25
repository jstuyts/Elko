package org.elkoserver.foundation.net.tcp.server

import org.elkoserver.foundation.net.ConnectionSetupFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

class TcpConnectionSetupFactory(
        private val props: ElkoProperties,
        private val tcpServerFactory: TcpServerFactory,
        private val baseConnectionSetupGorgel: Gorgel,
        private val listenerGorgel: Gorgel,
        private val traceFactory: TraceFactory,
        private val inputGorgel: Gorgel,
        private val jsonByteIOFramerWithoutLabelGorgel: Gorgel,
        private val mustSendDebugReplies: Boolean) : ConnectionSetupFactory {
    override fun create(label: String?, host: String, auth: AuthDesc, secure: Boolean, propRoot: String, actorFactory: MessageHandlerFactory) =
            TcpConnectionSetup(label, host, auth, secure, props, propRoot, tcpServerFactory, actorFactory, baseConnectionSetupGorgel, listenerGorgel, traceFactory, inputGorgel, jsonByteIOFramerWithoutLabelGorgel, mustSendDebugReplies)
}
