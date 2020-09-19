package org.elkoserver.foundation.server

import org.elkoserver.foundation.server.metadata.AuthDesc

class ListenerConfiguration(val auth: AuthDesc, val allow: Collection<String>, val protocol: String, val label: String?, val secure: Boolean)
