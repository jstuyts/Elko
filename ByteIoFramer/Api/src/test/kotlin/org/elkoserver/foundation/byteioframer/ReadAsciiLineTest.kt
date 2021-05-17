package org.elkoserver.foundation.byteioframer

import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadAsciiLineTest {
    @Test
    fun eofResultsInNull() {
        val input = ByteArrayInputStream(byteArrayOf())

        val result = readAsciiLine { input.read() }

        assertNull(result)
    }

    @Test
    fun lfResultsInEmptyString() {
        val input = ByteArrayInputStream(byteArrayOf(LF))

        val result = readAsciiLine { input.read() }

        assertEquals("", result)
    }

    @Test
    fun charLfResultsInChar() {
        val input = ByteArrayInputStream(byteArrayOf(A, LF))

        val result = readAsciiLine { input.read() }

        assertEquals("a", result)
    }

    @Test
    fun crLfResultsInEmptyString() {
        val input = ByteArrayInputStream(byteArrayOf(CR, LF))

        val result = readAsciiLine { input.read() }

        assertEquals("", result)
    }

    @Test
    fun nulLfResultsInEmptyString() {
        val input = ByteArrayInputStream(byteArrayOf(0, LF))

        val result = readAsciiLine { input.read() }

        assertEquals("", result)
    }

    @Test
    fun nonAsciiResultsInError() {
        assertThrows<IOException> {
            val input = ByteArrayInputStream(byteArrayOf(NON_ASCII_BYTE, LF))

            readAsciiLine { input.read() }
        }
    }

    // FIXME: Is this the correct behavior?
    @Test
    fun charEofResultsInError() {
        assertThrows<IOException> {
            val input = ByteArrayInputStream(byteArrayOf(SOME_ASCII_BYTE))

            readAsciiLine { input.read() }
        }
    }
}
