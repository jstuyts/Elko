package org.elkoserver.util

/**
 * Convert an array of bytes into a string suitable for output to a log.
 *
 * Such data is usually UTF-8, or ASCII (which is a subset of UTF-8), but
 * sometimes it is just random binary data.  If we simply use Java's
 * built-in charset conversion, it can produce illegible binary crud in the
 * server log.  Instead we convert it ourselves, rendering printable ASCII
 * bytes as the chars they represent and non-printable bytes as hexadecimal
 * character literal escape sequences.  This will have the downside of
 * sometimes rendering valid Unicode chars funny, but nearly everything we
 * actually care about seeing when debugging is printable ASCII.
 *
 * @param buf  Byte array containing data to be converted.
 * @param offset  Index of start position to convert.
 * @param length  Number of bytes to convert.
 *
 * @return a String rendering the indicated bytes in a legible form
 * suitable for logging.
 */
fun byteArrayToAscii(buf: ByteArray, offset: Int, length: Int): String {
    val chars = StringBuilder(length)
    (0 until length)
        .map { buf[offset + it] }
        .forEach {
            if (it in NON_CONTROL_CHARACTERS || it == LINE_FEED_AS_BYTE || it == CARRIAGE_RETURN_AS_BYTE || it == TAB_AS_BYTE) {
                chars.append(it.toInt().toChar())
            } else {
                chars.append(String.format("\\x%02x", it))
            }
        }
    return chars.toString()
}

private const val CARRIAGE_RETURN_AS_BYTE = '\r'.code.toByte()
private const val LINE_FEED_AS_BYTE = '\n'.code.toByte()
private const val SPACE_AS_BYTE = ' '.code.toByte()
private const val TAB_AS_BYTE = '\t'.code.toByte()
private const val TILDE_AS_BYTE = '~'.code.toByte()
private val NON_CONTROL_CHARACTERS = SPACE_AS_BYTE .. TILDE_AS_BYTE
