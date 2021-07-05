package org.elkoserver.server.context

import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.util.trace.slf4j.Gorgel

class SessionFactory(
        val server: Server,
        private val sessionGorgel: Gorgel,
        private val sessionCommGorgel: Gorgel,
        private val sessionPassword: String?,
        private val shutdownWatcher: ShutdownWatcher
) {

    fun create(contextor: Contextor): Session =
            Session(contextor, sessionPassword, sessionGorgel, shutdownWatcher, sessionCommGorgel)
}
