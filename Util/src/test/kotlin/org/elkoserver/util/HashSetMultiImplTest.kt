package org.elkoserver.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HashSetMultiImplTest {
    @Test
    fun additionResultsInNonEmptySet() {
        val set = HashSetMultiImpl<String>()

        set.add(SOME_VALUE)

        assertFalse(set.isEmpty)
    }

    @Test
    fun additionResultsInSetContainingElement() {
        val set = HashSetMultiImpl<String>()

        set.add("value")

        assertTrue(set.contains("value"))
    }

    @Test
    fun additionResultsInIteratorContainingElement() {
        val set = HashSetMultiImpl<String>()

        set.add("value")

        assertEquals("value", set.iterator().next())
    }

    @Test
    fun additionAndRemovalResultsInEmptySet() {
        val set = HashSetMultiImpl<String>()

        set.add(SOME_VALUE)
        set.remove(SOME_VALUE)

        assertTrue(set.isEmpty)
    }

    @Test
    fun additionAndRemovalResultsInSetNotContainingElement() {
        val set = HashSetMultiImpl<String>()

        set.add("value")
        set.remove("value")

        assertFalse(set.contains("value"))
    }

    @Test
    fun additionAndRemovalResultsInIteratorNotContainingElement() {
        val set = HashSetMultiImpl<String>()

        set.add("value")
        set.remove("value")

        assertFalse(set.iterator().hasNext())
    }

    @Test
    fun additionAndAdditionAndRemovalResultsInNonEmptySet() {
        val set = HashSetMultiImpl<String>()

        set.add(SOME_VALUE)
        set.add(SOME_VALUE)
        set.remove(SOME_VALUE)

        assertFalse(set.isEmpty)
    }

    @Test
    fun additionAndAdditionAndRemovalResultsInSetContainingElement() {
        val set = HashSetMultiImpl<String>()

        set.add("value")
        set.add("value")
        set.remove("value")

        assertTrue(set.contains("value"))
    }

    @Test
    fun additionAndAdditionAndRemovalResultsInIteratorContainingElement() {
        val set = HashSetMultiImpl<String>()

        set.add("value")
        set.add("value")
        set.remove("value")

        assertEquals("value", set.iterator().next())
    }

    @Test
    fun additionAndRemovalAndRemovalResultsInEmptySet() {
        val set = HashSetMultiImpl<String>()

        set.add(SOME_VALUE)
        set.remove(SOME_VALUE)
        set.remove(SOME_VALUE)

        assertTrue(set.isEmpty)
    }

    @Test
    fun additionAndRemovalAndRemovalResultsInSetNotContainingElement() {
        val set = HashSetMultiImpl<String>()

        set.add("value")
        set.remove("value")
        set.remove("value")

        assertFalse(set.contains("value"))
    }

    @Test
    fun additionAndRemovalAndRemovalResultsInIteratorNotContainingElement() {
        val set = HashSetMultiImpl<String>()

        set.add("value")
        set.remove("value")
        set.remove("value")

        assertFalse(set.iterator().hasNext())
    }

    @Test
    fun additionAndAdditionResultsInIteratorContainingElementOnce() {
        val set = HashSetMultiImpl<String>()

        set.add("value")
        set.add("value")

        val iterator = set.iterator()
        assertEquals("value", iterator.next())
        assertFalse(iterator.hasNext())
    }
}

private const val SOME_VALUE = "some value"
