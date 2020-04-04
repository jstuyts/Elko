package org.elkoserver.foundation.boot

import org.elkoserver.foundation.properties.ElkoProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

internal class BootArgumentsTest {
    @Test
    fun `fails if no arguments given`() {
        assertFailsWith(IllegalArgumentException::class) {
            BootArguments()
        }
    }

    @Test
    fun `single argument is found as main class`() {
        val bootArguments = BootArguments("mainClass")

        assertEquals("mainClass", bootArguments.mainClassName)
    }

    @Test
    fun `first argument is found as main class`() {
        val bootArguments = BootArguments("mainClass", "someArgument")

        assertEquals("mainClass", bootArguments.mainClassName)
    }

    @Test
    fun `main class before properties is found`() {
        val bootArguments = BootArguments("mainClass", "someProperty=someValue")

        assertEquals("mainClass", bootArguments.mainClassName)
    }

    @Test
    fun `main class between properties is found`() {
        val bootArguments = BootArguments("someProperty=someValue", "mainClass", "someProperty=someValue")

        assertEquals("mainClass", bootArguments.mainClassName)
    }

    @Test
    fun `main class after properties is found`() {
        val bootArguments = BootArguments("someProperty=someValue", "mainClass")

        assertEquals("mainClass", bootArguments.mainClassName)
    }

    @Test
    fun `no properties results in empty boot properties`() {
        val bootArguments = BootArguments("someMainClass")

        assertEquals(ElkoProperties(), bootArguments.bootProperties)
    }

    @Test
    fun `single property is added to boot properties`() {
        val bootArguments = BootArguments("theKey=theValue", "someMainClass")

        assertEquals("theValue", bootArguments.bootProperties.getProperty("theKey"))
    }

    @Test
    fun `single property without value is added to boot properties with empty string`() {
        val bootArguments = BootArguments("theKey=", "someMainClass")

        assertEquals("", bootArguments.bootProperties.getProperty("theKey"))
    }

    @Test
    fun `last value of duplicate property is returned in boot properties`() {
        val bootArguments = BootArguments("theKey=firstValue", "theKey=secondValue", "someMainClass")

        assertEquals("secondValue", bootArguments.bootProperties.getProperty("theKey"))
    }

    @Test
    fun `equals sign in property value is returned`() {
        val bootArguments = BootArguments("theKey==", "someMainClass")

        assertEquals("=", bootArguments.bootProperties.getProperty("theKey"))
    }

    @Test
    fun `all properties are returned in boot properties`() {
        val bootArguments = BootArguments("firstKey=firstValue", "secondKey=secondValue", "someMainClass")

        assertEquals(ElkoProperties(mapOf("firstKey" to "firstValue", "secondKey" to "secondValue")), bootArguments.bootProperties)
    }

    @Test
    fun `empty key is not interpreted as property`() {
        val bootArguments = BootArguments("someMainClass", "=someValue")

        assertEquals(ElkoProperties(), bootArguments.bootProperties)
    }
}
