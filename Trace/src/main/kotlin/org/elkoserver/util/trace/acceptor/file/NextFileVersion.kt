package org.elkoserver.util.trace.acceptor.file

import java.io.File

internal class NextFileVersion(myFile: File) {

    private val myDir: String

    private val myBasename: String

    private fun constructVersion(sequence: Int): File = File(myDir, myBasename + String.format("%03d", sequence))

    /**
     * Return a sequence number, if the given filename contains one.  If it
     * does not contain one, return -1.  Do not call this method unless
     * mightBeVersion has approved the filename.
     */
    private fun getSeq(filename: String): Int {
        val possibleSeqString = filename.substring(myBasename.length)
        return try {
            possibleSeqString.toInt()
        } catch (e: NumberFormatException) {
            -1
        }
    }

    /**
     * True iff the filename is of a format that could be a backup version of
     * the original file.  It remains to be determined whether it truly
     * contains a sequence number.
     *
     * In the interest of platform independence, the check is case-
     * insensitive.  This obeys Windows conventions about what "same files"
     * are, not Unix conventions.
     *
     * @param filename a filename, not including any directory part.
     */
    private fun mightBeVersion(filename: String): Boolean {
        // FIXME: Using lower case won't work reliably in all locales: https://stackoverflow.com/a/20301720/2622278
        val lowerCaseFilename = filename.toLowerCase()
        val minLen = myBasename.length + 1
        return filename.length >= minLen && lowerCaseFilename.startsWith(myBasename.toLowerCase())
    }

    fun nextAvailableVersion(): File {
        val files = File(myDir).list() ?: return File(myDir, "${myBasename}000")

        var highestSeq = -1
        for (file in files) {
            if (mightBeVersion(file)) {
                val possibleSeq = getSeq(file)
                if (possibleSeq >= 0 && possibleSeq > highestSeq) {
                    highestSeq = possibleSeq
                }
            }
        }
        return constructVersion(highestSeq + 1)
    }

    init {
        val myName = myFile.name
        myDir = myFile.parent
        myBasename = "$myName."
    }
}
