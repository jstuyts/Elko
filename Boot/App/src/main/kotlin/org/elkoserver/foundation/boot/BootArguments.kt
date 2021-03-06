package org.elkoserver.foundation.boot

import org.elkoserver.foundation.properties.ElkoProperties

internal class BootArguments internal constructor(vararg arguments: String) {
    internal val mainClassName: String

    internal val bootProperties: ElkoProperties

    init {
        val argumentsThatAreNotProperties = ArrayList<String>(arguments.size)
        val properties = HashMap<String, String>(arguments.size)
        scanArgumentsForProperties(arguments, properties, argumentsThatAreNotProperties)
        require(1 <= argumentsThatAreNotProperties.size) { "Boot needs class name to boot" }
        mainClassName = argumentsThatAreNotProperties[0]
        bootProperties = ElkoProperties(properties)
    }

    private fun scanArgumentsForProperties(arguments: Array<out String>, destinationProperties: MutableMap<String, String>, destinationArgumentsThatAreNotProperties: MutableList<String>) {
        arguments.forEach { argument ->
            scanArgumentForProperty(argument, destinationProperties, destinationArgumentsThatAreNotProperties)
        }
    }

    private fun scanArgumentForProperty(argument: String, destinationProperties: MutableMap<String, String>, destinationArgumentsThatAreNotProperties: MutableList<String>) {
        val indexOfEqualsSign = argument.indexOf('=')
        if (0 < indexOfEqualsSign) {
            val key = argument.take(indexOfEqualsSign)
            val value = argument.substring(indexOfEqualsSign + 1)
            destinationProperties[key] = value
        } else {
            destinationArgumentsThatAreNotProperties.add(argument)
        }
    }
}
