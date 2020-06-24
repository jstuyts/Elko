package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.metadata.AuthDesc

internal interface ConnectionSetupFactory {
    fun create(
            label: String?,
            host: String,
            auth: AuthDesc,
            secure: Boolean,
            propRoot: String,
            actorFactory: MessageHandlerFactory): ConnectionSetup
}
