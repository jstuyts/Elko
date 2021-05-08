package org.elkoserver.objectdatabase.store.filestore

import java.io.File

internal class MultipleFileOperations(private vararg val fileOperations: FileOperations) : FileOperations {
    private var nextIndex = 0

    override fun read(file: File) = getFileOperations().read(file)

    override fun write(file: File, text: String) {
        getFileOperations().write(file, text)
    }

    private fun getFileOperations(): FileOperations {
        val result = fileOperations[nextIndex]
        nextIndex += 1
        return result
    }
}