package org.elkoserver.json

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject

@Throws(JsonDecodingException::class)
fun JsonObject.getStringOrNull(key: String) =
        when (val value = get(key)) {
            is String -> value
            null -> null
            else -> throw JsonDecodingException()
        }

@Throws(JsonDecodingException::class)
fun JsonObject.getOptionalBoolean(key: String, default: Boolean) =
        when (val value = get(key)) {
            is Boolean -> value
            null -> default
            else -> throw JsonDecodingException()
        }

@Throws(JsonDecodingException::class)
fun JsonObject.getOptionalDouble(key: String, default: Double) =
        when (val value = get(key)) {
            is Double -> value
            null -> default
            else -> throw JsonDecodingException()
        }

@Throws(JsonDecodingException::class)
fun JsonObject.getOptionalInt(key: String, default: Int) =
        when (val value = get(key)) {
            is Int -> value
            null -> default
            else -> throw JsonDecodingException()
        }

@Throws(JsonDecodingException::class)
fun JsonObject.getOptionalLong(key: String, default: Long) =
        when (val value = get(key)) {
            is Long -> value
            null -> default
            else -> throw JsonDecodingException()
        }

@Throws(JsonDecodingException::class)
fun JsonObject.getOptionalString(key: String, default: String) =
        when (val value = get(key)) {
            is String -> value
            null -> default
            else -> throw JsonDecodingException()
        }

@Throws(JsonDecodingException::class)
fun JsonObject.getRequiredArray(key: String) =
        when (val value = get(key)) {
            is JsonArray -> value
            else -> throw JsonDecodingException()
        }

@Throws(JsonDecodingException::class)
fun JsonObject.getRequiredInt(key: String) =
        when (val value = get(key)) {
            is Int -> value
            else -> throw JsonDecodingException()
        }

@Throws(JsonDecodingException::class)
fun JsonObject.getRequiredObject(key: String) =
        when (val value = get(key)) {
            is JsonObject -> value
            else -> throw JsonDecodingException()
        }

@Throws(JsonDecodingException::class)
fun JsonObject.getRequiredString(key: String) =
        when (val value = get(key)) {
            is String -> value
            else -> throw JsonDecodingException()
        }
