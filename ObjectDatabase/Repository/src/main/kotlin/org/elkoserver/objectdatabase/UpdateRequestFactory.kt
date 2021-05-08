package org.elkoserver.objectdatabase

import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.json.Encodable
import java.util.function.Consumer

class UpdateRequestFactory(private val tagGenerator: IdGenerator) {
    internal fun create(ref: String, version: Int, obj: Encodable, handler: Consumer<Any?>?): PendingRequest {
        val tag = tagGenerator.generate().toString()
        return PendingRequest(handler, ref, tag, msgUpdate(ref, tag, version, obj))
    }
}
