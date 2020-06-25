package org.elkoserver.foundation.net

import org.elkoserver.foundation.server.metadata.AuthDesc

interface ConnectionSetupFactory {
    fun create(
            label: String?,
            host: String,
            auth: AuthDesc,
            secure: Boolean,
            propRoot: String,
            actorFactory: MessageHandlerFactory): ConnectionSetup
}
