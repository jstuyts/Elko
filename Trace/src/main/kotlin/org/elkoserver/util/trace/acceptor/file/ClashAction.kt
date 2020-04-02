package org.elkoserver.util.trace.acceptor.file

import java.io.File

/* Behaviors when opening files that already exist */
internal sealed class ClashAction {

    internal abstract fun versionFile(file: File): File

    internal object Overwrite : ClashAction() {
        override fun versionFile(file: File) = File(file.parent, "${file.name}.000")
    }

    internal object Add : ClashAction() {
        override fun versionFile(file: File) = NextFileVersion(file).nextAvailableVersion()
    }
}
