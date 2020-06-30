package org.elkoserver.foundation.byteioframer.json

import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStreamFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag

class JSONByteIOFramerFactoryFactory(private val gorgel: Gorgel, private val chunkyByteArrayInputStreamFactory: ChunkyByteArrayInputStreamFactory, private val mustSendDebugReplies: Boolean) {
    fun create() =
            JSONByteIOFramerFactory(gorgel, chunkyByteArrayInputStreamFactory, mustSendDebugReplies)

    fun create(label: String) =
            JSONByteIOFramerFactory(gorgel.withAdditionalStaticTags(Tag("label", label)), chunkyByteArrayInputStreamFactory, mustSendDebugReplies)
}
