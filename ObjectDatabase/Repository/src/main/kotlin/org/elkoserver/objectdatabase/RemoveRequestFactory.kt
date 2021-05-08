package org.elkoserver.objectdatabase

import org.elkoserver.idgeneration.IdGenerator
import java.util.function.Consumer

class RemoveRequestFactory(private val tagGenerator: IdGenerator) {
    internal fun create(ref: String, handler: Consumer<Any?>?): PendingRequest {
        val tag = tagGenerator.generate().toString()
        return PendingRequest(handler, ref, tag, msgRemove(ref, tag))
    }
}
