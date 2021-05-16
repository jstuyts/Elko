package org.elkoserver.util

interface HashMapMulti<K : Any, V : Any> {
    fun getMulti(key: K): HashSetMulti<V>

    fun containsKey(key: K): Boolean

    fun keys(): Set<K>

    fun values(): Iterable<V>
}
