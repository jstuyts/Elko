package org.elkoserver.server.context

import org.elkoserver.foundation.server.Server
import org.elkoserver.util.trace.slf4j.Gorgel

class SessionFactory(
        val server: Server,
        private val sessionGorgel: Gorgel,
        private val sessionCommGorgel: Gorgel,
        private val sessionPassword: String?) {

    fun create(contextor: Contextor): Session =
            Session(contextor, sessionPassword, sessionGorgel, sessionCommGorgel)
}
