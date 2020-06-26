package org.elkoserver.foundation.byteioframer.websocket

import org.elkoserver.util.trace.slf4j.Gorgel

class WebsocketByteIOFramerFactoryFactory(
        private val jsonByteIOFramerGorgel: Gorgel,
        private val websocketFramerGorgel: Gorgel,
        private val inputGorgel: Gorgel,
        private val mustSendDebugReplies: Boolean) {
    fun create(hostAddress: String, socketURI: String) =
            WebsocketByteIOFramerFactory(jsonByteIOFramerGorgel, websocketFramerGorgel, hostAddress, socketURI, inputGorgel, mustSendDebugReplies)
}