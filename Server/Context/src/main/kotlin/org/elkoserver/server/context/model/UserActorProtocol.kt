package org.elkoserver.server.context.model

import org.elkoserver.json.JsonLiteral

interface UserActorProtocol {
    fun connectionID(): Int

    fun exitContext(context: Context?)

    fun doDisconnect()

    val protocol: String

    fun send(message: JsonLiteral)
}