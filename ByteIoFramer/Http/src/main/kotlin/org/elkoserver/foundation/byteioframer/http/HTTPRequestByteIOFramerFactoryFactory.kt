package org.elkoserver.foundation.byteioframer.http

import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

class HTTPRequestByteIOFramerFactoryFactory(private val traceFactory: TraceFactory, private val inputGorgel: Gorgel) {
    fun create() =
            HTTPRequestByteIOFramerFactory(traceFactory, inputGorgel)
}
