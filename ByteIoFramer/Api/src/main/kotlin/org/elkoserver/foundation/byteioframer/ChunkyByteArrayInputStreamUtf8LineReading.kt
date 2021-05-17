package org.elkoserver.foundation.byteioframer

import java.io.EOFException
import java.io.IOException


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
internal fun readUtf8Line(charSupplier: CharSupplier, amWebsocketFraming: Boolean): String? = readLine {
    readUtf8Char(charSupplier, amWebsocketFraming)
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
private fun readUtf8Char(input: CharSupplier, amWebsocketFraming: Boolean): Int {
    val byteA = input.read()
    return when {
        /* EOF */
        byteA == END_OF_FILE_MARKER -> END_OF_FILE_MARKER
        /* Return a NUL; it will be ignored */
        amWebsocketFraming && byteA == WEBSOCKET_START_OF_FRAME_MARKER_AS_INT -> 0
        amWebsocketFraming && byteA == WEBSOCKET_END_OF_FRAME_MARKER_AS_INT -> LINE_FEED_AS_INT
        /* One byte UTF-8 character */
        byteA and 0x80 == 0 -> byteA
        /* Two byte UTF-8 character */
        byteA and 0xE0 == 0xC0 -> readLast1Of2ByteUtf8Character(input, byteA)
        /* Three byte UTF-8 character */
        byteA and 0xF0 == 0xE0 -> readLast2Of3ByteUtf8Character(input, byteA)
        /* Four byte UTF-8 character */
        byteA and 0xF0 == 0xF0 -> readLast3Of4ByteUtf8Character(input, byteA)
        else -> throwBadUtf8EncodingException()
    }
}

private fun readLast1Of2ByteUtf8Character(
    input: CharSupplier,
    byteA: Int
): Int {
    val byteB = input.read()
    return when {
        byteB and 0xC0 == 0x80 -> {
            byteA and 0x1F shl 6 or
                    (byteB and 0x3F)
        }
        else -> throwBadUtf8EncodingException()
    }
}

private fun readLast2Of3ByteUtf8Character(
    input: CharSupplier,
    byteA: Int
): Int {
    val byteB = input.read()
    return when {
        byteB and 0xC0 == 0x80 -> readLast1Of3ByteUtf8Character(input, byteA, byteB)
        else -> throwBadUtf8EncodingException()
    }
}

private fun readLast1Of3ByteUtf8Character(
    input: CharSupplier,
    byteA: Int,
    byteB: Int
): Int {
    val byteC = input.read()
    return when {
        byteC and 0xC0 == 0x80 -> {
            byteA and 0x0F shl 12 or
                    (byteB and 0x3F shl 6) or
                    (byteC and 0x3F)
        }
        else -> throwBadUtf8EncodingException()
    }
}

private fun readLast3Of4ByteUtf8Character(
    input: CharSupplier,
    byteA: Int
): Int {
    val byteB = input.read()
    return when {
        byteB and 0xC0 == 0x80 -> readLast2Of4ByteUtf8Character(input, byteA, byteB)
        else -> throwBadUtf8EncodingException()
    }
}

private fun readLast2Of4ByteUtf8Character(
    input: CharSupplier,
    byteA: Int,
    byteB: Int
): Int {
    val byteC = input.read()
    return when {
        byteC and 0xC0 == 0x80 -> readLast1Of4ByteUtf8Character(input, byteA, byteB, byteC)
        else -> throwBadUtf8EncodingException()
    }
}

private fun readLast1Of4ByteUtf8Character(
    input: CharSupplier,
    byteA: Int,
    byteB: Int,
    byteC: Int
): Int {
    val byteD = input.read()
    return when {
        byteD and 0xC0 == 0x80 -> {
            (byteA and 0x07 shl 18 or
                    (byteB and 0x3F shl 12) or
                    (byteC and 0x3F shl 6) or
                    (byteD and 0x3F)) + 0x10000
        }
        else -> throwBadUtf8EncodingException()
    }
}

private fun throwBadUtf8EncodingException(): Nothing {
    throw IOException("bad UTF-8 encoding")
}

private const val LINE_FEED_AS_INT = '\n'.code

private const val WEBSOCKET_END_OF_FRAME_MARKER_AS_INT = 0xFF
private const val WEBSOCKET_START_OF_FRAME_MARKER_AS_INT = 0x00
