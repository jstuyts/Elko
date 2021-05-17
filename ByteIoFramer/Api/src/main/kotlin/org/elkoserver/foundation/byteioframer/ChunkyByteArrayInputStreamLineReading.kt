package org.elkoserver.foundation.byteioframer

import java.io.EOFException
import java.io.IOException

internal fun interface CharSupplier {
    fun read(): Int
}

/**
 * Common read logic for readAsciiLine() and readUtf8Line().
 *
 * @return the next line of characters in the input, or null if another complete line is not currently available.
 *
 * @throws EOFException if the true end of input is reached.
 */
@Throws(IOException::class)
internal fun readLine(charSupplier: CharSupplier): String? {
    var inCharCode = charSupplier.read()
    return if (inCharCode == END_OF_FILE_MARKER) {
        null
    } else {
        var inChar = inCharCode.toChar()
        if (inChar == '\n') {
            ""
        } else {
            val myLine = StringBuilder(1000)
            do {
                when {
                    inCharCode == END_OF_FILE_MARKER -> throw IOException("EOF before end of line")
                    0x10FFFF < inCharCode -> throw IOException("Not a Unicode code point: $inCharCode")
                    inCharCode in 0x10000..0x10FFFF -> {
                        val first = (((inCharCode - 0x10000) shr 10) or 0xD800).toChar()
                        val second = ((inCharCode and 0x3FF) or 0xDC00).toChar()
                        println("${first.code.toString(16)} - ${second.code.toString(16)}")
                        myLine.append(first)
                        myLine.append(second)
                    }
                    inChar != '\r' && inChar.code != 0 -> myLine.append(inChar)
                }
                inCharCode = charSupplier.read()
                inChar = inCharCode.toChar()
            } while (inChar != '\n')
            myLine.toString()
        }
    }
}
