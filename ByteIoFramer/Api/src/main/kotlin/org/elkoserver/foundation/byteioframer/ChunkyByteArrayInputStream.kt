package org.elkoserver.foundation.byteioframer

import java.io.InputStream

abstract class ChunkyByteArrayInputStream : InputStream() {
    abstract fun addBuffer(buf: ByteArray, length: Int)

    abstract fun preserveBuffers()

    abstract fun updateUsefulByteCount(byteCount: Int)

    abstract fun readBytes(count: Int): ByteArray?

    abstract fun enableWebsocketFraming()

    abstract fun readAsciiLine(): String?

    abstract fun readUtf8Line(): String?
}
