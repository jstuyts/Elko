package org.elkoserver.objectdatabase.store.mongostore

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParserException
import org.bson.Document
import org.elkoserver.json.JsonParsing
import org.elkoserver.json.getOptionalDouble
import org.elkoserver.json.getRequiredArray
import org.elkoserver.json.getStringOrNull

internal fun jsonLiteralToMongoDocument(objStr: String, ref: String): Document {
    val obj: JsonObject = try {
        JsonParsing.jsonObjectFromString(objStr) ?: throw IllegalStateException()
    } catch (e: JsonParserException) {
        throw IllegalStateException(e)
    }
    val result = jsonObjectToDMongoDocument(obj)
    result["ref"] = ref

    // WARNING: the following is a rather profound and obnoxious modularity
    // boundary violation, but as ugly as it is, it appears to be the least
    // bad way to accommodate some of the limitations of Mongodb's
    // geo-indexing feature.  In order to spatially index an object,
    // Mongodb requires the 2D coordinate information to be stored in a
    // 2-element object or array property at the top level of the object to
    // be indexed. In the case of a 2-element object, the order the
    // properties appear in the JSON encoding is meaningful, which totally
    // violates the definition of JSON but that's what they did.
    // Unfortunately, the rest of our object encoding/decoding
    // infrastructure requires object-valued properties whose values are
    // polymorphic classes to contain a "type" property to indicate what
    // class they are.  Since there's no way to control the order in which
    // properties will be encoded when the object is serialized to JSON, we
    // risk having Mongodb mistake the type tag for the latitude or
    // longitude.  Even if we could overcome this, we'd still risk having
    // Mongodb mix the latitude and longitude up with each other.
    // Consequently, what we do is notice if an object being written has a
    // "pos" property of type "geopos", and if so we manually generate an
    // additional "_qpos_" property that is well formed according to
    // MongoDB's 2D coordinate encoding rules, and have Mongodb index
    // *that*.  When an object is read from the database, we strip this
    // property off again before we return the object to the application.
    val mods = obj.getRequiredArray("mods")
    mods.iterator().forEachRemaining { mod: Any? ->
        if (mod is JsonObject) {
            if ("geopos" == mod.getStringOrNull("type")) {
                val lat = mod.getOptionalDouble("lat", 0.0)
                val lon = mod.getOptionalDouble("lon", 0.0)
                val qpos = Document()
                qpos["lat"] = lat
                qpos["lon"] = lon
                result["_qpos_"] = qpos
            }
        }
    }
    // End of ugly modularity boundary violation
    return result
}

internal fun jsonObjectToDMongoDocument(obj: JsonObject): Document {
    val result = Document()
    for ((key, value) in obj.entries) {
        result[key] = valueToMongoValue(value)
    }
    return result
}

private fun valueToMongoValue(value: Any?) =
        if (value is JsonObject) {
            jsonObjectToDMongoDocument(value)
        } else if (value is JsonArray) {
            jsonArrayToMongoList(value)
        } else if (value is Long) {
            if (Int.MIN_VALUE <= value && value <= Int.MAX_VALUE) {
                value.toInt()
            } else {
                value
            }
        } else {
            value
        }

private fun jsonArrayToMongoList(arr: JsonArray): List<Any?> {
    val result = ArrayList<Any?>(arr.size)
    arr.mapTo(result, ::valueToMongoValue)
    return result
}
