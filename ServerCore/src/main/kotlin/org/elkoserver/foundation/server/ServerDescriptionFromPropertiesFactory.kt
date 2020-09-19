package org.elkoserver.foundation.server

import org.elkoserver.foundation.properties.ElkoProperties

class ServerDescriptionFromPropertiesFactory(private val props: ElkoProperties) {
    fun create(serverType: String): ServerDescription {
        val serverName = props.getProperty("conf.$serverType.name", "<anonymous>")
        val serviceName = props.getProperty("conf.$serverType.service")?.let { "-$it" } ?: ""
        return ServerDescription(serverName, serviceName)
    }
}
