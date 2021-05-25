package org.elkoserver.foundation.server

sealed class ObjectDatabaseConfiguration

object DirectObjectDatabaseConfiguration : ObjectDatabaseConfiguration()

object RepositoryObjectDatabaseConfiguration : ObjectDatabaseConfiguration()
