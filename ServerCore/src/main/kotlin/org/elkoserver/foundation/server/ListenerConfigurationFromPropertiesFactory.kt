package org.elkoserver.foundation.server

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.util.tokenize

class ListenerConfigurationFromPropertiesFactory(private val props: ElkoProperties, private val authDescFromPropertiesFactory: AuthDescFromPropertiesFactory) {
    fun read(propRoot: String): ListenerConfiguration {
        val auth = authDescFromPropertiesFactory.fromProperties(propRoot)
        val allowString = props.getProperty("$propRoot.allow")
        val allow = allowString?.tokenize(',')?.toSet() ?: emptySet()
        val protocol = props.getProperty("$propRoot.protocol", "tcp")
        val label = props.getProperty("$propRoot.label")
        val secure = props.testProperty("$propRoot.secure")
        return ListenerConfiguration(auth, allow, protocol, label, secure)
    }
}
