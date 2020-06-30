package org.elkoserver.foundation.byteioframer.http

import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStreamFactory
import org.elkoserver.util.trace.TraceFactory

class HTTPRequestByteIOFramerFactoryFactory(private val traceFactory: TraceFactory, private val chunkyByteArrayInputStreamFactory: ChunkyByteArrayInputStreamFactory) {
    fun create() =
            HTTPRequestByteIOFramerFactory(traceFactory, chunkyByteArrayInputStreamFactory)
}
