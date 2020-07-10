package org.elkoserver.objdb

import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.json.Encodable
import java.util.function.Consumer

class UpdateRequestFactory(private val tagGenerator: IdGenerator) {
    internal fun create(ref: String, version: Int, obj: Encodable, collectionName: String?, handler: Consumer<Any?>?): PendingRequest {
        val tag = tagGenerator.generate().toString()
        return PendingRequest(handler, ref, collectionName, tag, msgUpdate(ref, tag, version, obj, collectionName))
    }
}
