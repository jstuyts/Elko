package org.elkoserver.foundation.net

interface ConnectionSetup {
    val protocol: String
    val serverAddress: String
    fun startListener(): NetAddr
}
