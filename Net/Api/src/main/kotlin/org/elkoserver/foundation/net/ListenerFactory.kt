package org.elkoserver.foundation.net

import org.elkoserver.foundation.byteioframer.ByteIoFramerFactory
import org.elkoserver.util.trace.slf4j.Gorgel

class ListenerFactory(private val gorgel: Gorgel) {
    fun create(
            localAddress: String,
            handlerFactory: MessageHandlerFactory,
            framerFactory: ByteIoFramerFactory,
            amSecure: Boolean): Listener =
            Listener(localAddress, handlerFactory, framerFactory, amSecure, gorgel)
}
