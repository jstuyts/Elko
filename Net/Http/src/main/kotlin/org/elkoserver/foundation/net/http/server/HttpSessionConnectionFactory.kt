package org.elkoserver.foundation.net.http.server

import org.elkoserver.foundation.net.LoadMonitor
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.slf4j.Gorgel
import java.time.Clock
import java.util.concurrent.Executor

class HttpSessionConnectionFactory(
        private val myRunner: Executor,
        private val myLoadMonitor: LoadMonitor,
        private val timer: Timer,
        private val clock: Clock,
        private val httpSessionConnectionGorgel: Gorgel,
        private val connectionCommGorgel: Gorgel,
        private val sessionIdGenerator: IdGenerator,
        private val connectionIdGenerator: IdGenerator) {
    fun create(sessionFactory: HttpMessageHandlerFactory): HttpSessionConnection =
            HttpSessionConnection(sessionFactory, httpSessionConnectionGorgel, myRunner, myLoadMonitor, sessionIdGenerator.generate(), timer, clock, connectionCommGorgel, connectionIdGenerator)
}
