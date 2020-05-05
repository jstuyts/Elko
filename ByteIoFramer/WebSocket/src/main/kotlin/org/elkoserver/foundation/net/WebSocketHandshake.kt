package org.elkoserver.foundation.net

internal class WebSocketHandshake(private val myVersion: Int, private val myBytes: ByteArray) {
    fun version() = myVersion

    fun bytes() = myBytes
}
