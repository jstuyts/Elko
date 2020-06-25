package org.elkoserver.foundation.net.zmq.server

import org.elkoserver.foundation.net.ConnectionSetupFactory
import org.elkoserver.foundation.net.LoadMonitor
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.time.Clock

class ZeromqConnectionSetupFactory(
        private val props: ElkoProperties,
        private val runner: Runner,
        private val loadMonitor: LoadMonitor,
        private val baseConnectionSetupGorgel: Gorgel,
        private val listenerGorgel: Gorgel,
        private val connectionBaseCommGorgel: Gorgel,
        private val inputGorgel: Gorgel,
        private val jsonByteIOFramerWithoutLabelGorgel: Gorgel,
        private val traceFactory: TraceFactory,
        private val connectionIdGenerator: IdGenerator,
        private val clock: Clock,
        private val mustSendDebugReplies: Boolean) : ConnectionSetupFactory {
    override fun create(label: String?, host: String, auth: AuthDesc, secure: Boolean, propRoot: String, actorFactory: MessageHandlerFactory) =
            ZeromqConnectionSetup(label, host, auth, secure, props, propRoot, runner, loadMonitor, actorFactory, baseConnectionSetupGorgel, listenerGorgel, connectionBaseCommGorgel, inputGorgel, jsonByteIOFramerWithoutLabelGorgel, traceFactory, connectionIdGenerator, clock, mustSendDebugReplies)
}
