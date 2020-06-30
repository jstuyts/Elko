package org.elkoserver.foundation.net.connectionretrier

import org.elkoserver.foundation.byteioframer.json.JSONByteIOFramerFactoryFactory
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.tcp.client.TcpClientFactory
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag

class ConnectionRetrierFactory(
        private val tcpClientFactory: TcpClientFactory,
        private val timer: Timer,
        private val connectionRetrierWithoutLabelGorgel: Gorgel,
        private val trace: Trace,
        private val jsonByteIOFramerFactoryFactory: JSONByteIOFramerFactoryFactory) {
    fun create(host: HostDesc, label: String, messageHandlerFactory: MessageHandlerFactory) =
            ConnectionRetrier(
                    host,
                    label,
                    tcpClientFactory,
                    messageHandlerFactory,
                    timer,
                    connectionRetrierWithoutLabelGorgel.withAdditionalStaticTags(Tag("label", label)),
                    trace,
                    jsonByteIOFramerFactoryFactory)
}