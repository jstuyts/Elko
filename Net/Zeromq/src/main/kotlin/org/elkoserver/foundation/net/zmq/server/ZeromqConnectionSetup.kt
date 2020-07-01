package org.elkoserver.foundation.net.zmq.server

import org.elkoserver.foundation.byteioframer.json.JSONByteIOFramerFactoryFactory
import org.elkoserver.foundation.net.BaseConnectionSetup
import org.elkoserver.foundation.net.LoadMonitor
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetAddr
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.slf4j.Gorgel
import java.time.Clock

class ZeromqConnectionSetup(
        label: String?,
        host: String,
        auth: AuthDesc,
        secure: Boolean,
        props: ElkoProperties,
        propRoot: String,
        private val runner: Runner,
        private val loadMonitor: LoadMonitor,
        private val actorFactory: MessageHandlerFactory,
        gorgel: Gorgel,
        private val connectionBaseCommGorgel: Gorgel,
        private val threadCommGorgel: Gorgel,
        private val idGenerator: IdGenerator,
        private val clock: Clock,
        private val jsonByteIOFramerFactoryFactory: JSONByteIOFramerFactoryFactory)
    : BaseConnectionSetup(
        label,
        host,
        auth,
        secure,
        props,
        propRoot,
        gorgel) {

    override val protocol = "zeromq"

    override fun tryToStartListener(): NetAddr {
        val framerFactory = jsonByteIOFramerFactoryFactory.create()
        val thread = ZeroMQThread(runner, loadMonitor, connectionBaseCommGorgel, idGenerator, clock, threadCommGorgel)
        return thread.listen(host, actorFactory, framerFactory, secure)
    }

    override val listenAddressDescription: String
        get() = serverAddress

    override val valueToCompareWithBind: String
        get() = host

    override val serverAddress: String
        get() = host
}
