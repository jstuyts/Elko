package org.elkoserver.foundation.byteioframer.websocket

import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStreamFactory
import org.elkoserver.foundation.byteioframer.json.JsonByteIoFramerFactory
import org.elkoserver.util.trace.slf4j.Gorgel

class WebsocketByteIoFramerFactoryFactory(
        private val websocketFramerGorgel: Gorgel,
        private val chunkyByteArrayInputStreamFactory: ChunkyByteArrayInputStreamFactory,
        private val jsonByteIoFramerFactory: JsonByteIoFramerFactory) {
    fun create(hostAddress: String, socketUri: String) =
            WebsocketByteIoFramerFactory(websocketFramerGorgel, hostAddress, socketUri, chunkyByteArrayInputStreamFactory, jsonByteIoFramerFactory)
}