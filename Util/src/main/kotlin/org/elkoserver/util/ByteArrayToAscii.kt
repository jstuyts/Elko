package org.elkoserver.util

object ByteArrayToAscii {
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
    @JvmStatic
    fun byteArrayToASCII(buf: ByteArray, offset: Int, length: Int): String {
        val chars = StringBuilder(length)
        for (i in 0 until length) {
            val b = buf[offset + i]
            if (' '.toByte() <= b && b <= '~'.toByte() || b == '\n'.toByte() || b == '\r'.toByte() || b == '\t'.toByte()) {
                chars.append(b.toChar())
            } else {
                chars.append(String.format("\\x%02x", b))
            }
        }
        return chars.toString()
    }
}
