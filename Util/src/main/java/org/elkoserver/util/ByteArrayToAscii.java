package org.elkoserver.util;

public class ByteArrayToAscii {

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
     *    suitable for logging.
     */
    public static String byteArrayToASCII(byte[] buf, int offset, int length) {
        StringBuilder chars = new StringBuilder(length);
        for (int i = 0; i < length; ++i) {
            byte b = buf[offset + i];
            if ((' ' <= b && b <= '~') ||
                    b == '\n' || b == '\r' || b == '\t') {
                chars.append((char) b);
            } else {
                chars.append(String.format("\\x%02x", b));
            }
        }
        return chars.toString();
    }
}
