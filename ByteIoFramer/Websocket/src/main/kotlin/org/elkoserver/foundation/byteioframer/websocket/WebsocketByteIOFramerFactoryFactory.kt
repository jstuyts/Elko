package org.elkoserver.foundation.byteioframer.websocket

import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStreamFactory
import org.elkoserver.foundation.byteioframer.json.JSONByteIOFramerFactory
import org.elkoserver.util.trace.slf4j.Gorgel

class WebsocketByteIOFramerFactoryFactory(
        private val websocketFramerGorgel: Gorgel,
        private val chunkyByteArrayInputStreamFactory: ChunkyByteArrayInputStreamFactory,
        private val jsonByteIOFramerFactory: JSONByteIOFramerFactory) {
    fun create(hostAddress: String, socketURI: String) =
            WebsocketByteIOFramerFactory(websocketFramerGorgel, hostAddress, socketURI, chunkyByteArrayInputStreamFactory, jsonByteIOFramerFactory)
}