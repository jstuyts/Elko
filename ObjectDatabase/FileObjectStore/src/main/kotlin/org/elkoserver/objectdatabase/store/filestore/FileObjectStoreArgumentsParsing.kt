package org.elkoserver.objectdatabase.store.filestore

import org.elkoserver.objectdatabase.store.ObjectStoreArguments
import java.io.File

internal fun ObjectStoreArguments.parse(): FileObjectStoreArguments {
    val dirname = props.getProperty("$propRoot.odjdb")
            ?: throw java.lang.IllegalStateException("no object database directory specified")
    val dir = File(dirname)
    check(dir.exists()) { "object database directory '$dirname' does not exist" }
    check(dir.isDirectory) { "requested object database directory $dirname is not a directory" }

    return FileObjectStoreArguments(dir)
}