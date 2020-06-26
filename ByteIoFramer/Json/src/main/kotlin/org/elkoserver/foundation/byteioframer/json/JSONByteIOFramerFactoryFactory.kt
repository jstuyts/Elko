package org.elkoserver.foundation.byteioframer.json

import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag

class JSONByteIOFramerFactoryFactory(private val gorgel: Gorgel, private val inputGorgel: Gorgel, private val mustSendDebugReplies: Boolean) {
    fun create() =
            JSONByteIOFramerFactory(gorgel, inputGorgel, mustSendDebugReplies)

    fun create(label: String) =
            JSONByteIOFramerFactory(gorgel.withAdditionalStaticTags(Tag("label", label)), inputGorgel, mustSendDebugReplies)
}
