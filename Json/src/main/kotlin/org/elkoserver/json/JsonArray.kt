package org.elkoserver.json

import com.grack.nanojson.JsonArray

// FIXME: This class is here because:
// - The toString() has complex behavior. Not sure if this is only for diagnostic purposes or for production use.
class JsonArray : Iterable<Any?> {
    @JvmField
    val impl: JsonArray

    constructor() : super() {
        impl = JsonArray()
    }

    constructor(collection: Collection<*>?) : super() {
        impl = JsonArray(collection)
    }

    override fun iterator(): Iterator<Any?> = JsonArrayIterator(impl.iterator())

    override fun toString() = JsonArraySerialization.literal(this, EncodeControl.forRepository).sendableString()

    fun toArray(): Array<Any> {
        val result = impl.toTypedArray()
        for (i in result.indices) {
            result[i] = JsonWrapping.wrapWithElkoJsonImplementationIfNeeded(result[i])
        }
        return result
    }

    fun size() = impl.size

    fun add(elem: Any?) {
        impl.add(JsonWrapping.wrapWithElkoJsonImplementationIfNeeded(elem))
    }
}

internal class JsonArrayIterator(private val impl: Iterator<Any?>) : Iterator<Any?> {
    override fun hasNext() = impl.hasNext()

    override fun next() = JsonWrapping.wrapWithElkoJsonImplementationIfNeeded(impl.next())
}
