package org.elkoserver.objectdatabase.store.mongostore

internal class MongoObjectStoreArguments(
        val host: String,
        val port: Int,
        val dbName: String,
        val collName: String
)
