package org.elkoserver.foundation.net

class WebSocketHandshake(private val myVersion: Int, private val myBytes: ByteArray) {
    fun version() = myVersion

    fun bytes() = myBytes
}
