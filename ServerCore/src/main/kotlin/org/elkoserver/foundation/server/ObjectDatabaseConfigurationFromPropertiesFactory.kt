package org.elkoserver.foundation.server

import org.elkoserver.foundation.properties.ElkoProperties

class ObjectDatabaseConfigurationFromPropertiesFactory(
    private val props: ElkoProperties,
    private val propRoot: String,
) {
    fun read() =
        when {
            propsContainDirectConfiguration() -> DirectObjectDatabaseConfiguration
            propsContainRepositoryConfiguration() -> RepositoryObjectDatabaseConfiguration
            else -> throw IllegalStateException()
        }

    private fun propsContainDirectConfiguration() =
        props.getProperty("$propRoot.odjdb") != null

    private fun propsContainRepositoryConfiguration() =
        props.getProperty("$propRoot.repository.host") != null ||
                props.getProperty("$propRoot.repository.service") != null
}
