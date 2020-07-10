package org.elkoserver.foundation.json

import org.elkoserver.util.trace.slf4j.Gorgel

class MessageDispatcherFactory(
        private val methodInvokerCommGorgel: Gorgel,
        private val jsonToObjectDeserializer: JsonToObjectDeserializer) {
    fun create(resolver: TypeResolver): MessageDispatcher =
            MessageDispatcher(resolver, methodInvokerCommGorgel, jsonToObjectDeserializer)
}
