package org.elkoserver.json

import org.elkoserver.json.EncodeControl.ForRepositoryEncodeControl

// FIXME: This class is here because:
// - The toString() has complex behavior. Not sure if this is only for diagnostic purposes or for production use.
// - The behavior of getters without default value.
class JsonObject {
    private val impl: com.grack.nanojson.JsonObject

    constructor() : super() {
        impl = com.grack.nanojson.JsonObject()
    }

    constructor(map: Map<out String, *>) : super() {
        val elkoMap: MutableMap<String, Any?> = HashMap()
        map.forEach { (key: String, value: Any?) -> elkoMap[key] = JsonWrapping.wrapWithElkoJsonImplementationIfNeeded(value) }
        impl = com.grack.nanojson.JsonObject(elkoMap)
    }

    override fun toString(): String =
            JsonObjectSerialization.literal(this, ForRepositoryEncodeControl).sendableString()

    @Throws(JsonDecodingException::class)
    fun getString(key: String): String {
        if (impl[key] !is String) {
            throw JsonDecodingException()
        }
        return impl.getString(key)
    }

    fun entrySet(): MutableSet<MutableMap.MutableEntry<String, Any>> = impl.entries

    fun put(key: String, value: Any?) {
        impl[key] = JsonWrapping.wrapWithElkoJsonImplementationIfNeeded(value)
    }

    fun getObject(key: String?, defaultValue: JsonObject?): JsonObject =
            JsonWrapping.wrapWithElkoJsonImplementationIfNeeded(impl.getObject(key, defaultValue?.impl)) as JsonObject

    fun getString(key: String, defaultValue: String): String = impl.getString(key, defaultValue)

    fun getStringOrNull(key: String): String? = impl.getString(key, null)

    fun getDouble(key: String?, defaultValue: Double): Double = impl.getDouble(key, defaultValue)

    fun getInt(key: String?, defaultValue: Int): Int = impl.getInt(key, defaultValue)

    fun getBoolean(key: String?, defaultValue: Boolean): Boolean = impl.getBoolean(key, defaultValue)

    fun getLong(key: String?, defaultValue: Long): Long = impl.getLong(key, defaultValue)

    @Throws(JsonDecodingException::class)
    fun getObject(key: String): JsonObject {
        if (impl[key] !is JsonObject && impl[key] !is com.grack.nanojson.JsonObject) {
            throw JsonDecodingException()
        }
        return JsonWrapping.wrapWithElkoJsonImplementationIfNeeded(impl.getObject(key)) as JsonObject
    }

    @Throws(JsonDecodingException::class)
    fun getInt(key: String): Int {
        if (impl[key] !is Int) {
            throw JsonDecodingException()
        }
        return impl.getInt(key)
    }

    fun getArray(key: String?, defaultValue: JsonArray?): JsonArray =
            JsonWrapping.wrapWithElkoJsonImplementationIfNeeded(impl.getArray(key, defaultValue?.impl)) as JsonArray
}
