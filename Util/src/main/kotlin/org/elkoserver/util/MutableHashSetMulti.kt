package org.elkoserver.util

interface MutableHashSetMulti<V : Any> : HashSetMulti<V>, MutableIterable<V> {
    fun add(obj: V)

    fun remove(obj: V)
}
