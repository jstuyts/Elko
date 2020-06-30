package org.elkoserver.foundation.byteioframer.rtcp

import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStreamFactory
import org.elkoserver.util.trace.slf4j.Gorgel

class RTCPRequestByteIOFramerFactoryFactory(private val gorgel: Gorgel, private val chunkyByteArrayInputStreamFactory: ChunkyByteArrayInputStreamFactory, private val mustSendDebugReplies: Boolean) {
    fun create() =
            RTCPRequestByteIOFramerFactory(gorgel, chunkyByteArrayInputStreamFactory, mustSendDebugReplies)
}
