package org.elkoserver.objectdatabase.store.filestore

import java.io.File

internal interface FileOperations {
    fun read(file: File): String

    fun write(file: File, text: String)
}
