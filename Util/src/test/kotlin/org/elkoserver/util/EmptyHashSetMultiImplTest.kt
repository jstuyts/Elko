package org.elkoserver.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmptyHashSetMultiImplTest {
    @Test
    fun emptyReportsBeingEmpty() {
        val set = HashSetMultiImpl<Any>()

        val isEmpty = set.isEmpty

        assertTrue(isEmpty)
    }

    @Test
    fun emptyReturnsEmptyIterator() {
        val set = HashSetMultiImpl<Any>()

        val hasNext = set.iterator().hasNext()

        assertFalse(hasNext)
    }
}
