package org.elkoserver.objectdatabase.store.filestore

import java.io.File

object NotImplementedFileOperations : FileOperations {
    override fun read(file: File) = TODO()

    override fun write(file: File, text: String) = TODO()
}
