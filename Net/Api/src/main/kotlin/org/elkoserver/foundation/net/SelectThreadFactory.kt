package org.elkoserver.foundation.net

import org.elkoserver.foundation.run.Runner
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.slf4j.Gorgel
import java.time.Clock
import javax.net.ssl.SSLContext

class SelectThreadFactory(
        private val runner: Runner,
        private val loadMonitor: LoadMonitor,
        private val clock: Clock,
        private val commGorgel: Gorgel,
        private val tcpConnectionCommGorgel: Gorgel,
        private val idGenerator: IdGenerator) {
    fun create(sslContext: SSLContext?) =
            SelectThread(
                    runner,
                    loadMonitor,
                    sslContext,
                    clock,
                    commGorgel,
                    tcpConnectionCommGorgel,
                    idGenerator)
}
