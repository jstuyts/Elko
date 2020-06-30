package org.elkoserver.foundation.net.tcp.server

import org.elkoserver.foundation.byteioframer.json.JSONByteIOFramerFactoryFactory
import org.elkoserver.foundation.net.ConnectionSetupFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.slf4j.Gorgel

class TcpConnectionSetupFactory(
        private val props: ElkoProperties,
        private val tcpServerFactory: TcpServerFactory,
        private val baseConnectionSetupGorgel: Gorgel,
        private val jsonByteIOFramerFactoryFactory: JSONByteIOFramerFactoryFactory) : ConnectionSetupFactory {
    override fun create(label: String?, host: String, auth: AuthDesc, secure: Boolean, propRoot: String, actorFactory: MessageHandlerFactory) =
            TcpConnectionSetup(label, host, auth, secure, props, propRoot, tcpServerFactory, actorFactory, baseConnectionSetupGorgel, jsonByteIOFramerFactoryFactory)
}
