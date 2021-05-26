package org.elkoserver.objectdatabase.propertiesbased

import org.elkoserver.foundation.properties.ElkoProperties

internal class ObjectDatabaseConfigurationFromPropertiesFactory(
    private val props: ElkoProperties,
    private val propRoot: String,
) {
    fun read() =
        when {
            propsContainDirectConfiguration() -> DirectObjectDatabaseConfiguration
            propsContainRepositoryConfiguration() -> RepositoryObjectDatabaseConfiguration
            else -> throw IllegalStateException("No database definition at: $propRoot, in $props")
        }

    private fun propsContainDirectConfiguration() =
        props.getProperty("$propRoot.odjdb") != null

    private fun propsContainRepositoryConfiguration() =
        props.getProperty("$propRoot.repository.host") != null ||
                props.getProperty("$propRoot.repository.service") != null
}
