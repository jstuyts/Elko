package org.elkoserver.foundation.net

import org.elkoserver.util.ByteArrayToAscii
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.LinkedList

/**
 * Input stream similar to ByteArrayInputStream but backed by an ongoing series
 * of byte arrays that can be added to during the stream object's lifetime.
 */
class ChunkyByteArrayInputStream(private val gorgel: Gorgel) : InputStream() {
    /*
     * This object embodies a couple of assumptions about the nature of what is
     * being read and how this stream is being used:
     *
     *     (1) The input bytes may be UTF-8 minimally encoded characters.
     *     (2) The content is line-oriented text, like JSON messages or HTTP
     *         headers.
     *
     * A regular Java InputStreamReader can't be used because it buffers too
     * aggressively.  Its reads would outrun knowledge of the encoding of what
     * is being read, making it impossible to mix character and byte input from
     * the same source.  It is necessary to be able to mix these because some
     * legitimate input streams are themselves mixed: HTTP headers are encoded
     * in ASCII (according to RFC 2616), whereas the HTTP bodies of interest
     * will often (though not necessarily) be UTF-8.
     *
     * This class also must do its own UTF-8 decoding, because it reads from
     * multiple byte arrays.  There is no guarantee that a UTF-8 character
     * won't be split across two arrays.  If such a split were to happen,
     * Java's UTF-8 byte stream decoder would get a decoding exception on the
     * fractional character.
     *
     * This class exploits the two assumptions given: The UTF-8 minimal
     * encoding rules ensure that a newline will be a single-byte ASCII newline
     * character and that this byte (0x0A) will never appear inside a
     * multi-byte UTF-8 character.  Therefore, a buffer may be scanned for a
     * newline without actually decoding it.  When a newline is detected, it is
     * safe to UTF-8 decode up through that newline, since the newline itself
     * is a complete character.  Conversely, if there is no newline at the end
     * of the buffer, then it is safe to wait for more bytes to be received:
     * additional bytes are to be expected because the content is, by
     * definition, line-oriented.
     *
     * This class works thusly: input bytes are handed to this object in
     * byte-array buffers via the addBuffer() method.  These buffers are saved
     * internally to serve future read() calls.  However, only bytes up through
     * the last received newline character are actually available for reading.
     * If read() is called when there are no newlines in the buffers, the
     * read() call returns an end-of-file indication (i.e., read length -1).
     * If more bytes are provided by a later call to addBuffer() AND these new
     * bytes contain one or more newline characters, then additional bytes (up
     * through the last newline) become available for reading.  A true
     * end-of-file in the input is handed to this object by passing a
     * zero-length buffer to addBuffer().  If there are then any bytes
     * following the last newline in the buffers, an IOException will be thrown
     * after that last newline is read.
     *
     * This behavior means that if readASCIILine() or readUTFLine() returns an
     * EOF, it just means that there isn't a complete line in the buffers, not
     * that the actual end of input has been encountered.  An actual (normal)
     * EOF condition is signalled to readers by throwing an EOFException.  This
     * is an admitted abuse of the interface spec for InputStream motivated by
     * pragmatic concerns; the consumer of the input from this stream MUST be
     * coded in awareness of what is being done here.
     */
    /** Byte buffer currently being read from.  */
    private var myWorkingBuffer: ByteArray?

    /** Position of the next byte to read from the working buffer.  */
    private var myWorkingBufferIdx = 0

    /** Number of bytes in working buffer that may be read.  */
    private var myWorkingBufferLength = 0

    /** Additional byte arrays queued to be read.  */
    private val myPendingBuffers = LinkedList<ByteArray>()

    /** A byte array that has been passed to this object by its client but not
     * yet copied to storage internal to this object.  */
    private var myClientBuffer: ByteArray?

    /** Number of bytes in client buffer that may be used.  */
    private var myClientBufferLength = 0

    /** Number of bytes fed in that haven't yet been read.  */
    private var myTotalByteCount: Int

    /** Number of unread bytes that can actually be returned right now.  */
    private var myUsefulByteCount: Int

    /** Flag indicating an actual EOF in the input.  */
    private var amAtEOF: Boolean

    /** Flag indicating that WebSocket framing is enabled.  */
    private var amWebSocketFraming: Boolean

    /**
     * Be given a buffer full of input bytes.
     *
     *
     * Note: this class assumes that it may continue to freely make direct
     * use of the contents of the byte buffer that is given to this method
     * (i.e., without copying it to internal storage) until [ ][.preserveBuffers] is called; after that, the buffer contents may be
     * modified externally.  This is somewhat delicate, but eliminates a vast
     * amount of unnecessary byte array allocation and copying.
     *
     * @param buf  The bytes themselves.
     * @param length  Number of bytes in 'buf' to read (&lt;= buf.length).
     */
    fun addBuffer(buf: ByteArray, length: Int) {
        gorgel.d?.run {
            if (length == 0) {
                debug("receiving 0 bytes: || (EOF)")
            } else {
                debug("receiving $length bytes: |${ByteArrayToAscii.byteArrayToASCII(buf, 0, length)}|")
            }
        }
        if (length == 0) {
            amAtEOF = true
        } else {
            preserveBuffers() /* save previous client buffer */
            myClientBuffer = buf
            myClientBufferLength = length
            (0 until length)
                    .filter { buf[it] == '\n'.toByte() || amWebSocketFraming && buf[it] == (-1).toByte() }
                    .forEach { myUsefulByteCount = myTotalByteCount + it + 1 }
            myTotalByteCount += length
        }
    }

    /**
     * Get the number of bytes that can be read from this input stream without
     * blocking.  Since this class never actually blocks, this is just the
     * number of bytes available at the moment.
     *
     * @return the number of bytes that can be read from this input stream.
     */
    override fun available(): Int = myTotalByteCount

    /**
     * Copy any unread portions of the client buffer passed to [ ][.addBuffer].  This has the side effect of passing responsibility for the
     * client buffer back to the client.  This indirection minimizes
     * unnecessary byte array allocation and copying.
     */
    fun preserveBuffers() {
        val currentClientBuffer = myClientBuffer
        if (currentClientBuffer != null) {
            val currentWorkingBuffer = myWorkingBuffer
            if (currentWorkingBuffer === currentClientBuffer) {
                val saveBuffer = currentWorkingBuffer.copyOfRange(myWorkingBufferIdx, myWorkingBufferLength)
                myWorkingBuffer = saveBuffer
                myWorkingBufferLength = saveBuffer.size
                myWorkingBufferIdx = 0
            } else {
                val saveBuffer = byteArrayOf(*currentClientBuffer)
                myPendingBuffers.add(saveBuffer)
            }
            myClientBuffer = null
        }
    }

    /**
     * Read the next byte of data from the input stream.  The byte value is
     * returned as an int in the range 0 to 255.  If no byte is available,
     * the value -1 is returned.
     *
     * @return the next byte of data, or -1 if the end of the currently
     * available input is reached.
     *
     * @throws IOException if an incomplete line is in the buffers upon the
     * true end of input.
     * @throws EOFException if the true end of input is reached normally
     */
    @Throws(IOException::class)
    override fun read(): Int {
        return if (testEnd()) {
            -1
        } else {
            readByteInternal()
        }
    }

    /**
     * Read the next actual byte from the input stream.  The byte value is
     * returned as an int in the range 0 to 255.  This method must only be
     * called if it is know that a byte is actually available!
     *
     * @return the next byte of data.
     */
    private fun readByteInternal(): Int {
        if (myWorkingBuffer == null) {
            if (myPendingBuffers.size > 0) {
                val nextBuffer = myPendingBuffers.removeFirst()
                myWorkingBuffer = nextBuffer
                myWorkingBufferLength = nextBuffer.size
            } else if (myClientBuffer != null) {
                myWorkingBuffer = myClientBuffer
                myWorkingBufferLength = myClientBufferLength
            } else {
                return -1
            }
            myWorkingBufferIdx = 0
        }
        val result = myWorkingBuffer!![myWorkingBufferIdx++].toInt()
        if (myWorkingBufferIdx >= myWorkingBufferLength) {
            if (myWorkingBuffer === myClientBuffer) {
                myClientBuffer = null
            }
            myWorkingBuffer = null
        }
        --myTotalByteCount
        if (myUsefulByteCount > 0) {
            --myUsefulByteCount
        }
        return result and 0xFF
    }

    /**
     * Read a fixed number of bytes from the input stream.  The result is
     * returned as a byte array of the requested length.  If insufficient data
     * is available, null is returned.
     *
     * @param count  The number of bytes desired
     *
     * @return an array of 'count' bytes, or null if that many bytes are not
     * currently available.
     */
    fun readBytes(count: Int): ByteArray? {
        return if (myTotalByteCount < count) {
            null
        } else {
            val result = ByteArray(count)
            for (i in 0 until count) {
                result[i] = readByteInternal().toByte()
            }
            result
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
    private fun readUTF8Char(): Int {
        val byteA = read()
        if (byteA == -1) {
            /* EOF */
            return -1
        } else if (amWebSocketFraming && byteA == 0x00) {
            /* WebSocket start-of-frame: return a nul; it will be ignored */
            return 0
        } else if (amWebSocketFraming && byteA == 0xFF) {
            /* WebSocket end-of-frame: pretend it's a newline */
            return '\n'.toInt()
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

    /**
     * Read the next line of raw ASCII characters from the input stream.
     * However, if a complete line is not available in the buffers, null is
     * returned.
     *
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
    fun readASCIILine(): String? = readLine(false)

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
    private fun readLine(doUTF8: Boolean): String? {
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
                    if (inCharCode != -1 && inChar != '\r' && inChar.toInt() != 0) {
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
     * Read the next line of UTF-8 encoded characters from the input stream.
     * However, If a complete line is not available in the buffers, null is
     * returned.
     *
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
    fun readUTF8Line(): String? = readLine(true)

    fun updateUsefulByteCount(byteCount: Int) {
        if (myUsefulByteCount < byteCount) {
            myUsefulByteCount = byteCount
        }
    }

    /**
     * Control the enabling of WebSocket framing.  If WebSocket framing is on,
     * messages are assumed to be framed between 0x00 and 0xFF bytes, per the
     * WebSockets standard.  The 0x00 bytes of any messages will be ignored,
     * but the 0xFF bytes will be translated into newlines (and will be
     * regarded as line terminators for purposes of determining the
     * availability of received data in the input buffers).
     *
     */
    fun enableWebSocketFraming() {
        amWebSocketFraming = true
    }

    /**
     * Test for the end of available input.
     *
     * @return true if no more input is available at this time, false if input
     * is available.
     *
     * @throws IOException if the true EOF is reached prior to an end of line.
     * @throws EOFException if the true EOF is reached properly.
     */
    @Throws(IOException::class)
    private fun testEnd(): Boolean {
        return if (myUsefulByteCount == 0) {
            if (amAtEOF) {
                if (myTotalByteCount > 0) {
                    throw IOException("undecodeable bytes left over")
                }
                throw EOFException()
            } else {
                true
            }
        } else {
            false
        }
    }

    /**
     * Constructor.  Initially, no input has been provided.
     */
    init {
        myWorkingBuffer = null
        myClientBuffer = null
        myTotalByteCount = 0
        myUsefulByteCount = 0
        amAtEOF = false
        amWebSocketFraming = false
    }
}