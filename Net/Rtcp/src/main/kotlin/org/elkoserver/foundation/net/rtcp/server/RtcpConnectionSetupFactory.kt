package org.elkoserver.foundation.net.rtcp.server

import org.elkoserver.foundation.net.ConnectionSetupFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.slf4j.Gorgel

class RtcpConnectionSetupFactory(
        private val props: ElkoProperties,
        private val rtcpServerFactory: RtcpServerFactory,
        private val baseConnectionSetupGorgel: Gorgel,
        private val rtcpMessageHandlerFactoryGorgel: Gorgel) : ConnectionSetupFactory {
    override fun create(label: String?, host: String, auth: AuthDesc, secure: Boolean, propRoot: String, actorFactory: MessageHandlerFactory): RtcpConnectionSetup =
            RtcpConnectionSetup(label, host, auth, secure, props, propRoot, rtcpServerFactory, actorFactory, baseConnectionSetupGorgel, rtcpMessageHandlerFactoryGorgel)
}
