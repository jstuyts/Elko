package org.elkoserver.objectdatabase.propertiesbased

internal sealed class ObjectDatabaseConfiguration

internal object DirectObjectDatabaseConfiguration : ObjectDatabaseConfiguration()

internal object RepositoryObjectDatabaseConfiguration : ObjectDatabaseConfiguration()
