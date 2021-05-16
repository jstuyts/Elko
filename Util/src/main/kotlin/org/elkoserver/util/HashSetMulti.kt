package org.elkoserver.util

interface HashSetMulti<V : Any> : Iterable<V> {
    operator fun contains(obj: V): Boolean

    val isEmpty: Boolean
}
