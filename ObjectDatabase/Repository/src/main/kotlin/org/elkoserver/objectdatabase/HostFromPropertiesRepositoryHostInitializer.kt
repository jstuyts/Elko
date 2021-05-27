package org.elkoserver.objectdatabase

import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory

class HostFromPropertiesRepositoryHostInitializer(
    private val hostDescFromPropertiesFactory: HostDescFromPropertiesFactory,
    private val odbPropRoot: String
) : RepositoryHostInitializer {
    override fun initialize(objectDatabaseRepository: ObjectDatabaseRepository) {
        objectDatabaseRepository.connectToRepository(hostDescFromPropertiesFactory.fromProperties(odbPropRoot))
    }
}