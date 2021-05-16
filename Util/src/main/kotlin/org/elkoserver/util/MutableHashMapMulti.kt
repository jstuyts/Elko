package org.elkoserver.util

interface MutableHashMapMulti<K : Any, V : Any> : HashMapMulti<K, V> {
    fun add(key: K, value: V)

    override fun getMulti(key: K): MutableHashSetMulti<V>

    fun remove(key: K, value: V)

    fun remove(key: K)
}
