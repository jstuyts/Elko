package org.elkoserver.objectdatabase

import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.json.JsonObject
import java.util.function.Consumer

class QueryRequestFactory(private val tagGenerator: IdGenerator) {
    internal fun create(template: JsonObject, collectionName: String?, maxResults: Int, handler: Consumer<Any?>?): PendingRequest {
        val tag = tagGenerator.generate().toString()
        return PendingRequest(handler, "query", collectionName, tag, msgQuery(template, tag, collectionName, maxResults))
    }
}
