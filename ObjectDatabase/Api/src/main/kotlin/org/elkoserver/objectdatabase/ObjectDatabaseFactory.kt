package org.elkoserver.objectdatabase

interface ObjectDatabaseFactory {
    fun create(): ObjectDatabaseBase
}
