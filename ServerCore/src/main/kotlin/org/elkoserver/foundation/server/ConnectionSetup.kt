package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.NetAddr

interface ConnectionSetup {
    val protocol: String
    val serverAddress: String
    fun startListener(): NetAddr
}
