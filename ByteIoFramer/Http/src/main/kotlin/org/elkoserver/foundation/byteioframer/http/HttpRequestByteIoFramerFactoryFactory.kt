package org.elkoserver.foundation.byteioframer.http

import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStreamFactory
import org.elkoserver.util.trace.slf4j.Gorgel

class HttpRequestByteIoFramerFactoryFactory(private val baseCommGorgel: Gorgel, private val chunkyByteArrayInputStreamFactory: ChunkyByteArrayInputStreamFactory) {
    fun create(): HttpRequestByteIoFramerFactory =
            HttpRequestByteIoFramerFactory(baseCommGorgel, chunkyByteArrayInputStreamFactory)
}
