package org.elkoserver.develop.gradle.mongodb

import com.mongodb.MongoClient
import org.bson.Document
import java.io.File

fun initializeMongodbCollection(mongodbHostAndPort: String, databaseName: String, collectionName: String, jsonFilesDirectory: File) {
    MongoClient(mongodbHostAndPort).use { mongoClient ->
        val collection = mongoClient.getDatabase(databaseName).getCollection(collectionName)
        collection.deleteMany(Document())
        jsonFilesDirectory.listFiles { file -> file.isFile && file.name.endsWith(".json") }?.forEach { jsonFile ->
            val json = jsonFile.readText()
            collection.insertOne(Document.parse(json))
        }
    }
}
