package org.elkoserver.objectdatabase.store.filestore

import org.elkoserver.objectdatabase.store.ObjectDesc
import java.io.File
import java.io.IOException

private val ERROR_MESSAGE = "read error"

object ReadErrorFileOperations : FileOperations {
    override fun read(file: File) = throw IOException(ERROR_MESSAGE)

    override fun write(file: File, text: String) = TODO()
}

fun readError(ref: String) = ObjectDesc(ref, null, ERROR_MESSAGE)
