package org.elkoserver.foundation.net

import org.elkoserver.foundation.byteioframer.ByteIoFramerFactory
import org.elkoserver.foundation.run.Runner
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.slf4j.Gorgel
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.time.Clock

class TcpConnectionFactory(
        private val runner: Runner,
        private val loadMonitor: LoadMonitor,
        private val clock: Clock,
        private val tcpConnectionGorgel: Gorgel,
        private val tcpConnectionCommGorgel: Gorgel,
        private val idGenerator: IdGenerator) {

    fun create(
            handlerFactory: MessageHandlerFactory,
            framerFactory: ByteIoFramerFactory,
            channel: SocketChannel,
            isSecure: Boolean,
            key: SelectionKey,
            selectThread: SelectThread): TcpConnection =
            TcpConnection(handlerFactory, framerFactory,
                    channel, key, selectThread, runner, loadMonitor, isSecure, tcpConnectionGorgel, clock, tcpConnectionCommGorgel, idGenerator)
}