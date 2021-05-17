package org.elkoserver.foundation.byteioframer

import org.elkoserver.util.trace.slf4j.Gorgel

class ChunkyByteArrayInputStreamFactory(private val gorgel: Gorgel) {
    fun create(): ChunkyByteArrayInputStream =
            ChunkyByteArrayInputStreamImpl(gorgel)
}