package org.elkoserver.foundation.byteioframer

import java.io.EOFException
import java.io.IOException

/**
 * Read the next line of raw ASCII characters from the input stream.
 * However, if a complete line is not available in the buffers, null is
 * returned.
 *
 * Takes ASCII characters from the buffers until a newline (optionally
 * preceded by a carriage return) is read, at which point the line is
 * returned as a String, not including the line terminator character(s).
 *
 * @return the next line of ASCII characters in the input, or null if
 * another complete line is not currently available.
 *
 * @throws EOFException if the true end of input is reached.
 */
@Throws(IOException::class)
internal fun readAsciiLine(charSupplier: CharSupplier): String? = readLine {
    val char = charSupplier.read()

    if (char < END_OF_FILE_MARKER || 127 < char) {
        throw IOException("Invalid ASCII byte: $char")
    }

    char
}
