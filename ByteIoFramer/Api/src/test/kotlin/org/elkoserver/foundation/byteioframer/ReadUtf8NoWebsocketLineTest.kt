package org.elkoserver.foundation.byteioframer

import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadUtf8NoWebsocketLineTest {
    @Test
    fun eofResultsInNull() {
        val input = ByteArrayInputStream(byteArrayOf())

        val result = readUtf8Line({ input.read() }, false)

        assertNull(result)
    }

    @Test
    fun lfResultsInEmptyString() {
        val input = ByteArrayInputStream(byteArrayOf(LF))

        val result = readUtf8Line({ input.read() }, false)

        assertEquals("", result)
    }

    @Test
    fun char1LfResultsInChar() {
        val input = ByteArrayInputStream(byteArrayOf(A, LF))

        val result = readUtf8Line({ input.read() }, false)

        assertEquals("a", result)
    }

    @Test
    fun firstChar2LfResultsInChar() {
        val input = ByteArrayInputStream(byteArrayOf(*"\u0080".toByteArray(), LF))

        val result = readUtf8Line({ input.read() }, false)

        assertEquals("\u0080", result)
    }

    @Test
    fun lastChar2LfResultsInChar() {
        val input = ByteArrayInputStream(byteArrayOf(*"\u07FF".toByteArray(), LF))

        val result = readUtf8Line({ input.read() }, false)

        assertEquals("\u07FF", result)
    }

    @Test
    fun only1OfChar2LfResultsInError() {
        assertThrows<IOException> {
            val input = ByteArrayInputStream(byteArrayOf(_C0, SOME_NON_UTF8_CONTINUATION_BYTE))

            readUtf8Line({ input.read() }, false)
        }
    }

    @Test
    fun firstChar3LfResultsInChar() {
        val input = ByteArrayInputStream(byteArrayOf(*"\u0800".toByteArray(), LF))

        val result = readUtf8Line({ input.read() }, false)

        assertEquals("\u0800", result)
    }

    @Test
    fun lastChar3LfResultsInChar() {
        val input = ByteArrayInputStream(byteArrayOf(*"\uFFFF".toByteArray(), LF))

        val result = readUtf8Line({ input.read() }, false)

        assertEquals("\uFFFF", result)
    }

    @Test
    fun only1OfChar3LfResultsInError() {
        assertThrows<IOException> {
            val input = ByteArrayInputStream(byteArrayOf(_E0, SOME_NON_UTF8_CONTINUATION_BYTE))

            readUtf8Line({ input.read() }, false)
        }
    }

    @Test
    fun only2OfChar3LfResultsInError() {
        assertThrows<IOException> {
            val input = ByteArrayInputStream(byteArrayOf(_E0, _80, SOME_NON_UTF8_CONTINUATION_BYTE))

            readUtf8Line({ input.read() }, false)
        }
    }

    @Test
    fun firstChar4LfResultsInChar() {
        val input = ByteArrayInputStream(byteArrayOf(_F0, _80, _80, _80, LF))

        val result = readUtf8Line({ input.read() }, false)

        assertEquals("\uD800\uDC00", result)
    }

    @Test
    fun lastChar4LfResultsInChar() {
        val input = ByteArrayInputStream(byteArrayOf(_F3, _BF, _BF, _BF, LF))

        val result = readUtf8Line({ input.read() }, false)

        assertEquals("\uDBFF\uDFFF", result)
    }

    @Test
    fun only1OfChar4LfResultsInError() {
        assertThrows<IOException> {
            val input = ByteArrayInputStream(byteArrayOf(_F0, SOME_NON_UTF8_CONTINUATION_BYTE))

            readUtf8Line({ input.read() }, false)
        }
    }

    @Test
    fun only2OfChar4LfResultsInError() {
        assertThrows<IOException> {
            val input = ByteArrayInputStream(byteArrayOf(_F0, _80, SOME_NON_UTF8_CONTINUATION_BYTE))

            readUtf8Line({ input.read() }, false)
        }
    }

    @Test
    fun only3OfChar4LfResultsInError() {
        assertThrows<IOException> {
            val input = ByteArrayInputStream(byteArrayOf(_F0, _80, _80, SOME_NON_UTF8_CONTINUATION_BYTE))

            readUtf8Line({ input.read() }, false)
        }
    }

    @Test
    fun crLfResultsInEmptyString() {
        val input = ByteArrayInputStream(byteArrayOf(CR, LF))

        val result = readUtf8Line({ input.read() }, false) 

        assertEquals("", result)
    }

    @Test
    fun nulLfResultsInEmptyString() {
        val input = ByteArrayInputStream(byteArrayOf(0, LF))

        val result = readUtf8Line({ input.read() }, false)

        assertEquals("", result)
    }

    @Test
    fun ffResultsInError() {
        assertThrows<IOException> {
            val input = ByteArrayInputStream(byteArrayOf(_FF))

            readUtf8Line({ input.read() }, false)
        }
    }

    // FIXME: Is this the correct behavior?
    @Test
    fun charEofResultsInError() {
        assertThrows<IOException> {
            val input = ByteArrayInputStream(byteArrayOf(SOME_ASCII_BYTE))

            readUtf8Line({ input.read() }, false)
        }
    }
}
