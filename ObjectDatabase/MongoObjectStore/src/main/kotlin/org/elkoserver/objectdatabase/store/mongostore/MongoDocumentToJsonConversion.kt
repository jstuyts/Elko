package org.elkoserver.objectdatabase.store.mongostore

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import org.bson.Document
import org.bson.types.ObjectId

internal fun mongoDocumentToJsonObject(mongoDocument: Document): JsonObject {
    val result = JsonObject()
    for (key in mongoDocument.keys) {
        if (!key.startsWith("_")) {
            var value = mongoDocument[key]
            if (value is List<*>) {
                value = mongoListToJsonArray(value)
            } else if (value is Document) {
                value = mongoDocumentToJsonObject(value)
            }
            result[key] = value
        } else if (key == "_id") {
            val oid = mongoDocument[key] as ObjectId
            result[key] = oid.toString()
        }
    }
    return result
}

private fun mongoListToJsonArray(mongoList: List<*>): JsonArray {
    val result = JsonArray()
    mongoList
            .map {
                when (it) {
                    is List<*> -> mongoListToJsonArray(it)
                    is Document -> mongoDocumentToJsonObject(it)
                    else -> it
                }
            }
            .forEach(result::add)
    return result
}
