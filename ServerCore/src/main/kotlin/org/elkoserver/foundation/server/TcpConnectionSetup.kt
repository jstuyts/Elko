package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.JSONByteIOFramerFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.tcp.server.TcpServerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

class TcpConnectionSetup(
        label: String?,
        host: String,
        auth: AuthDesc,
        secure: Boolean,
        props: ElkoProperties,
        propRoot: String,
        private val tcpServerFactory: TcpServerFactory,
        actorFactory: MessageHandlerFactory,
        gorgel: Gorgel,
        listenerGorgel: Gorgel,
        traceFactory: TraceFactory,
        private val inputGorgel: Gorgel,
        private val jsonByteIOFramerGorgel: Gorgel,
        private val mustSendDebugReplies: Boolean)
    : BaseTcpConnectionSetup(label, host, auth, secure, props, propRoot, actorFactory, gorgel, listenerGorgel, traceFactory) {
    override val protocol = "tcp"

    @Throws(IOException::class)
    override fun createListenAddress() =
            tcpServerFactory.listenTCP(bind, actorFactory, secure, JSONByteIOFramerFactory(jsonByteIOFramerGorgel, inputGorgel, mustSendDebugReplies), msgTrace)
}
