package org.elkoserver.foundation.net.rtcp.server

import org.elkoserver.foundation.net.LoadMonitor
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.slf4j.Gorgel
import java.time.Clock
import java.util.concurrent.Executor

class RtcpSessionConnectionFactory(
        private val rtcpSessionConnectionGorgel: Gorgel,
        private val connectionCommGorgel: Gorgel,
        private val myRunner: Executor,
        private val myLoadMonitor: LoadMonitor,
        private val timer: Timer,
        private val clock: Clock,
        private val sessionIdGenerator: IdGenerator,
        private val connectionIdGenerator: IdGenerator) {

    fun create(sessionFactory: RtcpMessageHandlerFactory): RtcpSessionConnection =
            RtcpSessionConnection(sessionFactory, myRunner, myLoadMonitor, sessionIdGenerator.generate(), timer, clock, rtcpSessionConnectionGorgel, connectionCommGorgel, connectionIdGenerator)
}