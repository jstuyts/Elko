package org.elkoserver.foundation.net.rtcp.server

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel

class RtcpMessageHandlerFactoryFactory(
        private val props: ElkoProperties,
        private val timer: Timer,
        private val rtcpMessageHandlerCommGorgel: Gorgel,
        private val rtcpSessionConnectionFactory: RtcpSessionConnectionFactory) {

    fun create(innerHandlerFactory: MessageHandlerFactory, rtcpMessageHandlerFactoryGorgel: Gorgel) =
            RtcpMessageHandlerFactory(innerHandlerFactory, rtcpMessageHandlerFactoryGorgel, rtcpSessionConnectionFactory, props, timer, rtcpMessageHandlerCommGorgel)
}
