package org.elkoserver.json

import com.grack.nanojson.JsonArray
import org.elkoserver.json.EncodeControl.ForRepositoryEncodeControl

// FIXME: This class is here because:
// - The toString() has complex behavior. Not sure if this is only for diagnostic purposes or for production use.
class JsonArray : Iterable<Any?> {
    val impl: JsonArray

    constructor() : super() {
        impl = JsonArray()
    }

    constructor(collection: Collection<*>?) : super() {
        impl = JsonArray(collection)
    }

    override fun iterator(): Iterator<Any?> = JsonArrayIterator(impl.iterator())

    override fun toString(): String = JsonArraySerialization.literal(this, ForRepositoryEncodeControl).sendableString()

    fun toArray(): Array<Any> {
        val result = impl.toTypedArray()
        for (i in result.indices) {
            result[i] = JsonWrapping.wrapWithElkoJsonImplementationIfNeeded(result[i])
        }
        return result
    }

    fun size(): Int = impl.size

    fun add(elem: Any?) {
        impl.add(JsonWrapping.wrapWithElkoJsonImplementationIfNeeded(elem))
    }
}

internal class JsonArrayIterator(private val impl: Iterator<Any?>) : Iterator<Any?> {
    override fun hasNext() = impl.hasNext()

    override fun next() = JsonWrapping.wrapWithElkoJsonImplementationIfNeeded(impl.next())
}
