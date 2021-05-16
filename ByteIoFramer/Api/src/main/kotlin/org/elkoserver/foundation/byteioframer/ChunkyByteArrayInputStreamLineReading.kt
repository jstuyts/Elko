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
fun ChunkyByteArrayInputStream.readASCIILine(): String? = readLine(false)

/**
 * Read the next line of UTF-8 encoded characters from the input stream.
 * However, If a complete line is not available in the buffers, null is
 * returned.
 *
 * Takes UTF-8 characters from the buffers until a newline (optionally
 * preceded by a carriage return) is read, at which point the line is
 * returned as a String, not including the line terminator character(s).
 *
 * @return the next line of UTF-8 characters in the input, or null if
 * another complete line is not currently available.
 *
 * @throws EOFException if the true end of input is reached.
 */
@Throws(IOException::class)
fun ChunkyByteArrayInputStream.readUTF8Line(): String? = readLine(true)

/**
 * Common read logic for readASCIILine() and readUTF8Line().
 *
 * @param doUTF8  If true, read UTF-8 characters; if false, read ASCII
 * characters.
 *
 * @return the next line of characters in the input according the doUTF8
 * flag, or null if another complete line is not currently available.
 *
 * @throws EOFException if the true end of input is reached.
 */
@Throws(IOException::class)
private fun ChunkyByteArrayInputStream.readLine(doUTF8: Boolean): String? {
    val myLine = StringBuilder(1000)
    var inCharCode = if (doUTF8) readUTF8Char() else read()
    return if (inCharCode == -1) {
        null
    } else {
        var inChar = inCharCode.toChar()
        if (inChar == '\n') {
            ""
        } else {
            do {
                if (inCharCode != -1 && inChar != '\r' && inChar.code != 0) {
                    myLine.append(inChar)
                }
                inCharCode = if (doUTF8) readUTF8Char() else read()
                inChar = inCharCode.toChar()
            } while (inChar != '\n')
            myLine.toString()
        }
    }
}

/**
 * Read the next UTF-8 encoded character from the input stream.  If
 * another full character is not available, -1 is returned, even if there
 * are still bytes remaining in the input stream.
 *
 * @return the next character in the input, or -1 if the end of the
 * currently available input is reached.
 *
 * @throws IOException if an incomplete line is in the buffers upon
 * encountering the true end of input.
 */
@Throws(IOException::class)
private fun ChunkyByteArrayInputStream.readUTF8Char(): Int {
    val byteA = read()
    if (byteA == -1) {
        /* EOF */
        return -1
    } else if (amWebsocketFraming && byteA == WEBSOCKET_START_OF_FRAME_MARKER_AS_INT) {
        /* WebSocket start-of-frame: return a nul; it will be ignored */
        return 0
    } else if (amWebsocketFraming && byteA == WEBSOCKET_END_OF_FRAME_MARKER_AS_INT) {
        /* WebSocket end-of-frame: pretend it's a newline */
        return LINE_FEED_AS_INT
    } else if (byteA and 0x80 == 0) {
        /* One byte UTF-8 character */
        return byteA
    } else if (byteA and 0xE0 == 0xC0) {
        /* Two byte UTF-8 character */
        val byteB = read()
        if (byteB and 0xC0 == 0x80) {
            return byteA and 0x1F shl 6 or
                    (byteB and 0x3F)
        }
    } else if (byteA and 0xF0 == 0xE0) {
        /* Three byte UTF-8 character */
        val byteB = read()
        if (byteB and 0xC0 == 0x80) {
            val byteC = read()
            if (byteC and 0xC0 == 0x80) {
                return byteA and 0x0F shl 12 or
                        (byteB and 0x3F shl 6) or
                        (byteC and 0x3F)
            }
        }
    }
    throw IOException("bad UTF-8 encoding")
}

private const val LINE_FEED_AS_INT = '\n'.code

private const val WEBSOCKET_END_OF_FRAME_MARKER_AS_INT = 0xFF
private const val WEBSOCKET_START_OF_FRAME_MARKER_AS_INT = 0x00
