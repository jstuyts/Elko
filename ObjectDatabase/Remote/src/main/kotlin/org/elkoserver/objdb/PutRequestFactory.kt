package org.elkoserver.objdb

import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.json.Encodable
import java.util.function.Consumer

class PutRequestFactory(private val tagGenerator: IdGenerator) {
    internal fun create(ref: String, obj: Encodable, collectionName: String?, requireNew: Boolean, handler: Consumer<Any?>?): PendingRequest {
        val tag = tagGenerator.generate().toString()
        return PendingRequest(handler, ref, collectionName, tag, msgPut(ref, tag, obj, collectionName, requireNew))
    }
}
