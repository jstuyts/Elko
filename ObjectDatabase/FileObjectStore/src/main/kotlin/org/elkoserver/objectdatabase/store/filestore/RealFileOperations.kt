package org.elkoserver.objectdatabase.store.filestore

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets

internal object RealFileOperations : FileOperations {
    override fun read(file: File) = file.readText()

    override fun write(file: File, text: String) {
        val objWriter: Writer = OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8)
        objWriter.write(text)
        objWriter.close()
    }
}