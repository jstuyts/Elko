package org.elkoserver.foundation.properties

import java.util.Objects
import java.util.Properties

/**
 * Enhanced version of the Java [Properties] class that knows how to pull
 * properties settings out of the command line and also provides a friendlier
 * interface to the values of the properties settings themselves.
 *
 * Creates an empty property collection with no default values.
 */
class ElkoProperties() {
    private val properties = Properties()

    constructor(properties: Map<String, String>) : this() {
        this.properties.putAll(properties)
    }

    fun containsProperty(property: String): Boolean = properties.containsKey(property)

    fun getProperty(property: String): String? = properties.getProperty(property)

    fun <TDefault : String?> getProperty(property: String, defaultValue: TDefault): TDefault = properties.getProperty(property, defaultValue) as TDefault

    fun stringPropertyNames(): Set<String> = properties.stringPropertyNames()

    /**
     * Get the value of a property as a boolean.  This will be true if the
     * property value is the string "true", false if the property value is
     * the string "false", and will be the default if the property value is
     * anything else or if the property is absent.
     *
     * @param property  The name of the property to test.
     *
     * @return the value of the property interpreted as a boolean.
     */
    private fun boolProperty(property: String) =
            when (properties.getProperty(property)) {
                "true" -> true
                "false" -> false
                else -> false
            }

    /**
     * Get the value of a property as a double.
     *
     * @param property  The name of the property of interest.
     * @param defaultValue  The default value in the event of absence or error.
     *
     * @return the value of the property interpreted as a double.
     */
    fun doubleProperty(property: String, defaultValue: Double): Double {
        val value = properties.getProperty(property)
        return if (value != null) {
            try {
                value.toDouble()
            } catch (e: NumberFormatException) {
                defaultValue
            }
        } else {
            defaultValue
        }
    }

    /**
     * Get the value of a property as an integer.
     *
     * @param property  The name of the property of interest.
     * @param defaultValue  The default value in the event of absence or error.
     *
     * @return the value of the property interpreted as an int.
     */
    fun intProperty(property: String, defaultValue: Int): Int {
        val value = properties.getProperty(property)
        return if (value != null) {
            try {
                value.toInt()
            } catch (e: NumberFormatException) {
                defaultValue
            }
        } else {
            defaultValue
        }
    }

    /**
     * Test the setting of a boolean property.  A boolean property is
     * `true` if its string value is "true", and `false` if its
     * string value is "false" or if the property is not set at all.
     *
     * @param property  The name of the property to test.
     *
     * @return the value of the property interpreted as a boolean.
     *
     * @throws IllegalArgumentException if the property has a value that is
     * neither of the strings "true" nor "false".
     */
    fun testProperty(property: String): Boolean = boolProperty(property)

    /**
     * Test if the value of a property is a particular string.
     *
     * @param property  The name of the property to test.
     * @param possibleValue  String value to test if it is equal to.
     *
     * @return `true` if the property has a value equal to
     * `possibleValue`.
     */
    fun testProperty(property: String, possibleValue: String?): Boolean =
            if (possibleValue != null) {
                possibleValue == properties.getProperty(property)
            } else {
                false
            }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val otherAsElkoProperties = other as ElkoProperties
        return properties == otherAsElkoProperties.properties
    }

    override fun hashCode(): Int = Objects.hash(properties)
}
