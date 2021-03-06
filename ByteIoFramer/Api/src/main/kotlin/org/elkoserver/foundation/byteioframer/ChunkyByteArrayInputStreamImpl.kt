package org.elkoserver.foundation.byteioframer

import org.elkoserver.util.byteArrayToAscii
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.EOFException
import java.io.IOException
import java.util.LinkedList

/**
 * Input stream similar to ByteArrayInputStream but backed by an ongoing series
 * of byte arrays that can be added to during the stream object's lifetime.
 */
internal class ChunkyByteArrayInputStreamImpl(private val gorgel: Gorgel) : ChunkyByteArrayInputStream(), CharSupplier {
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
     * This behavior means that if readAsciiLine() or readUtf8Line() returns an
     * EOF, it just means that there isn't a complete line in the buffers, not
     * that the actual end of input has been encountered.  An actual (normal)
     * EOF condition is signalled to readers by throwing an EOFException.  This
     * is an admitted abuse of the interface spec for InputStream motivated by
     * pragmatic concerns; the consumer of the input from this stream MUST be
     * coded in awareness of what is being done here.
     */
    /** Byte buffer currently being read from.  */
    private var myWorkingBuffer: ByteArray? = null

    /** Position of the next byte to read from the working buffer.  */
    private var myWorkingBufferIdx = 0

    /** Number of bytes in working buffer that may be read.  */
    private var myWorkingBufferLength = 0

    /** Additional byte arrays queued to be read.  */
    private val myPendingBuffers = LinkedList<ByteArray>()

    /** A byte array that has been passed to this object by its client but not
     * yet copied to storage internal to this object.  */
    private var myClientBuffer: ByteArray? = null

    /** Number of bytes in client buffer that may be used.  */
    private var myClientBufferLength = 0

    /** Number of bytes fed in that haven't yet been read.  */
    private var myTotalByteCount = 0

    /** Number of unread bytes that can actually be returned right now.  */
    private var myUsefulByteCount = 0

    /** Flag indicating an actual EOF in the input.  */
    private var amAtEOF = false

    /** Flag indicating that WebSocket framing is enabled.  */
    private var amWebsocketFraming = false

    /**
     * Be given a buffer full of input bytes.
     *
     * Note: this class assumes that it may continue to freely make direct
     * use of the contents of the byte buffer that is given to this method
     * (i.e., without copying it to internal storage) until [preserveBuffers] is called; after that, the buffer contents may be
     * modified externally.  This is somewhat delicate, but eliminates a vast
     * amount of unnecessary byte array allocation and copying.
     *
     * @param buf  The bytes themselves.
     * @param length  Number of bytes in 'buf' to read (&lt;= buf.length).
     */
    override fun addBuffer(buf: ByteArray, length: Int) {
        gorgel.d?.run {
            if (length == 0) {
                debug("receiving 0 bytes: || (EOF)")
            } else {
                debug("receiving $length bytes: |${byteArrayToAscii(buf, 0, length)}|")
            }
        }
        if (length == 0) {
            amAtEOF = true
        } else {
            preserveBuffers() /* save previous client buffer */
            myClientBuffer = buf
            myClientBufferLength = length
            (0 until length)
                .filter { isEndOfLineMarker(buf, it) }
                .forEach { indexOfEndOfLineMarker -> myUsefulByteCount = myTotalByteCount + indexOfEndOfLineMarker + 1 }
            myTotalByteCount += length
        }
    }

    private fun isEndOfLineMarker(buf: ByteArray, index: Int) =
        isLineFeed(buf, index) || isWebsocketEndOfLineMarker(buf, index)

    private fun isLineFeed(buf: ByteArray, index: Int) = buf[index] == LINE_FEED_AS_BYTE

    private fun isWebsocketEndOfLineMarker(buf: ByteArray, index: Int) =
        amWebsocketFraming && buf[index] == WEBSOCKET_END_OF_FRAME_MARKER_AS_BYTE

    /**
     * Get the number of bytes that can be read from this input stream without
     * blocking.  Since this class never actually blocks, this is just the
     * number of bytes available at the moment.
     *
     * @return the number of bytes that can be read from this input stream.
     */
    override fun available(): Int = myTotalByteCount

    /**
     * Copy any unread portions of the client buffer passed to [addBuffer(ByteArray, Int)][addBuffer].  This has the side effect of passing responsibility for the
     * client buffer back to the client.  This indirection minimizes
     * unnecessary byte array allocation and copying.
     */
    override fun preserveBuffers() {
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
            END_OF_FILE_MARKER
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
            when {
                0 < myPendingBuffers.size -> {
                    val nextBuffer = myPendingBuffers.removeFirst()
                    myWorkingBuffer = nextBuffer
                    myWorkingBufferLength = nextBuffer.size
                }
                myClientBuffer != null -> {
                    myWorkingBuffer = myClientBuffer
                    myWorkingBufferLength = myClientBufferLength
                }
                else -> return END_OF_FILE_MARKER
            }
            myWorkingBufferIdx = 0
        }
        val result = myWorkingBuffer!![myWorkingBufferIdx++].toInt() and 0xFF
        if (myWorkingBufferLength <= myWorkingBufferIdx) {
            if (myWorkingBuffer === myClientBuffer) {
                myClientBuffer = null
            }
            myWorkingBuffer = null
        }
        --myTotalByteCount
        if (0 < myUsefulByteCount) {
            --myUsefulByteCount
        }
        return result
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
    override fun readBytes(count: Int) =
        if (myTotalByteCount < count) {
            null
        } else {
            ByteArray(count) { readByteInternal().toByte() }
        }

    override fun updateUsefulByteCount(byteCount: Int) {
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
    override fun enableWebsocketFraming() {
        amWebsocketFraming = true
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
                if (0 < myTotalByteCount) {
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

    override fun readAsciiLine() = readAsciiLine(this)

    override fun readUtf8Line() = readUtf8Line(this, amWebsocketFraming)
}

internal const val END_OF_FILE_MARKER = -1

private const val LINE_FEED_AS_BYTE = '\n'.code.toByte()

private const val WEBSOCKET_END_OF_FRAME_MARKER_AS_BYTE = (-1).toByte()
