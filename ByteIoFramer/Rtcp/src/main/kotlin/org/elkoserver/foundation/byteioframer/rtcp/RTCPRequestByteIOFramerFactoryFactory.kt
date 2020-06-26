package org.elkoserver.foundation.byteioframer.rtcp

import org.elkoserver.util.trace.slf4j.Gorgel

class RTCPRequestByteIOFramerFactoryFactory(private val gorgel: Gorgel, private val inputGorgel: Gorgel, private val mustSendDebugReplies: Boolean) {
    fun create() =
            RTCPRequestByteIOFramerFactory(gorgel, inputGorgel, mustSendDebugReplies)
}
