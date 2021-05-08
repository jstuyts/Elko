package org.elkoserver.objectdatabase.store.mongostore

import org.elkoserver.objectdatabase.store.ObjectStoreArguments

internal fun ObjectStoreArguments.parse(): MongoObjectStoreArguments {
    val mongoPropRoot = "$propRoot.odjdb.mongo"
    val addressStr = props.getProperty("$mongoPropRoot.hostport")
            ?: throw IllegalStateException("no mongo database server address specified")
    val colon = addressStr.indexOf(':')
    val port: Int
    val host: String
    if (colon < 0) {
        port = 27017
        host = addressStr
    } else {
        port = addressStr.substring(colon + 1).toInt()
        host = addressStr.take(colon)
    }
    val dbName = props.getProperty("$mongoPropRoot.dbname", "elko")
    val collName = props.getProperty("$mongoPropRoot.collname", "odb")

    return MongoObjectStoreArguments(host, port, dbName, collName)
}
