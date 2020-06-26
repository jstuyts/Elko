package org.elkoserver.foundation.net

import org.elkoserver.foundation.byteioframer.ByteIOFramerFactory
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.slf4j.Gorgel

class ListenerFactory(private val gorgel: Gorgel) {
    fun create(
            localAddress: String,
            handlerFactory: MessageHandlerFactory,
            framerFactory: ByteIOFramerFactory,
            amSecure: Boolean,
            tcpConnectionTrace: Trace) =
            Listener(localAddress, handlerFactory, framerFactory, amSecure, gorgel, tcpConnectionTrace)
}
