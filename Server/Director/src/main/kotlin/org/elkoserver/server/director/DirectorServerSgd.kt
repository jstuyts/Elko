@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.director

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStream
import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStreamFactory
import org.elkoserver.foundation.byteioframer.http.HttpRequestByteIoFramerFactoryFactory
import org.elkoserver.foundation.byteioframer.json.JsonByteIoFramer
import org.elkoserver.foundation.byteioframer.json.JsonByteIoFramerFactoryFactory
import org.elkoserver.foundation.byteioframer.rtcp.RtcpRequestByteIoFramerFactoryFactory
import org.elkoserver.foundation.byteioframer.websocket.WebsocketByteIoFramerFactory
import org.elkoserver.foundation.byteioframer.websocket.WebsocketByteIoFramerFactoryFactory
import org.elkoserver.foundation.json.AlwaysBaseTypeResolver
import org.elkoserver.foundation.json.BaseCommGorgelInjector
import org.elkoserver.foundation.json.ClassspecificGorgelInjector
import org.elkoserver.foundation.json.ClockInjector
import org.elkoserver.foundation.json.ConstructorInvoker
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.json.MessageDispatcherFactory
import org.elkoserver.foundation.json.MethodInvoker
import org.elkoserver.foundation.net.BaseConnectionSetup
import org.elkoserver.foundation.net.Communication.COMMUNICATION_CATEGORY_TAG
import org.elkoserver.foundation.net.Listener
import org.elkoserver.foundation.net.ListenerFactory
import org.elkoserver.foundation.net.SelectThread
import org.elkoserver.foundation.net.SslContextSgd
import org.elkoserver.foundation.net.TcpConnection
import org.elkoserver.foundation.net.TcpConnectionFactory
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrier
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.net.http.server.HttpConnectionSetupFactory
import org.elkoserver.foundation.net.http.server.HttpMessageHandler
import org.elkoserver.foundation.net.http.server.HttpMessageHandlerFactory
import org.elkoserver.foundation.net.http.server.HttpServerFactory
import org.elkoserver.foundation.net.http.server.HttpSessionConnection
import org.elkoserver.foundation.net.http.server.HttpSessionConnectionFactory
import org.elkoserver.foundation.net.http.server.JsonHttpFramer
import org.elkoserver.foundation.net.rtcp.server.RtcpConnectionSetupFactory
import org.elkoserver.foundation.net.rtcp.server.RtcpMessageHandler
import org.elkoserver.foundation.net.rtcp.server.RtcpMessageHandlerFactory
import org.elkoserver.foundation.net.rtcp.server.RtcpMessageHandlerFactoryFactory
import org.elkoserver.foundation.net.rtcp.server.RtcpServerFactory
import org.elkoserver.foundation.net.rtcp.server.RtcpSessionConnection
import org.elkoserver.foundation.net.rtcp.server.RtcpSessionConnectionFactory
import org.elkoserver.foundation.net.tcp.client.TcpClientFactory
import org.elkoserver.foundation.net.tcp.server.TcpConnectionSetupFactory
import org.elkoserver.foundation.net.tcp.server.TcpServerFactory
import org.elkoserver.foundation.net.ws.server.WebsocketConnectionSetupFactory
import org.elkoserver.foundation.net.ws.server.WebsocketServerFactory
import org.elkoserver.foundation.net.zmq.server.ZeromqConnectionSetupFactory
import org.elkoserver.foundation.net.zmq.server.ZeromqThread
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.foundation.run.thread.ThreadRunner
import org.elkoserver.foundation.server.BrokerActor
import org.elkoserver.foundation.server.BrokerActorFactory
import org.elkoserver.foundation.server.LoadWatcher
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServerLoadMonitor
import org.elkoserver.foundation.server.ServiceActor
import org.elkoserver.foundation.server.ServiceActorFactory
import org.elkoserver.foundation.server.ServiceLink
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.LongIdGenerator
import org.elkoserver.idgeneration.RandomIdGenerator
import org.elkoserver.objdb.GetRequestFactory
import org.elkoserver.objdb.ObjDbActor
import org.elkoserver.objdb.ObjDbLocal
import org.elkoserver.objdb.ObjDbLocalFactory
import org.elkoserver.objdb.ObjDbLocalRunnerFactory
import org.elkoserver.objdb.ObjDbRemote
import org.elkoserver.objdb.ObjDbRemoteFactory
import org.elkoserver.objdb.PutRequestFactory
import org.elkoserver.objdb.QueryRequestFactory
import org.elkoserver.objdb.RemoveRequestFactory
import org.elkoserver.objdb.UpdateRequestFactory
import org.elkoserver.ordinalgeneration.LongOrdinalGenerator
import org.elkoserver.server.director.Director.Companion.DEFAULT_ESTIMATED_LOAD_INCREMENT
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.opt
import org.ooverkommelig.providedByMe
import org.ooverkommelig.req
import java.security.SecureRandom
import java.time.Clock

internal class DirectorServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase, SslContextSgd.Provided {
        fun timer(): D<Timer>
        fun clock(): D<Clock>
        override fun props(): D<ElkoProperties>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
        override fun sslContextPropertyNamePrefix(): D<String> = providedByMe()
        override fun sslContextSgdGorgel(): D<Gorgel> = providedByMe()
    }

    val sslContextSgd = add(SslContextSgd(object : SslContextSgd.Provided by provided {
        override fun sslContextSgdGorgel() = sslContextSgdGorgel
        override fun sslContextPropertyNamePrefix() = sslContextPropertyNamePrefix
    }, configuration))

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class) }

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(DirectorBoot::class) }

    val brokerActorGorgel by Once { req(provided.baseGorgel()).getChild(BrokerActor::class, COMMUNICATION_CATEGORY_TAG) }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val directorGorgel by Once { req(provided.baseGorgel()).getChild(Director::class) }

    val directorActorGorgel by Once { req(provided.baseGorgel()).getChild(DirectorActor::class, COMMUNICATION_CATEGORY_TAG) }

    val directorActorCommGorgel by Once { req(directorActorGorgel).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

    val jsonHttpFramerCommGorgel by Once { req(provided.baseGorgel()).getChild(JsonHttpFramer::class).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }
    val tcpConnectionGorgel by Once { req(provided.baseGorgel()).getChild(TcpConnection::class) }
    val jsonByteIoFramerWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(JsonByteIoFramer::class) }
    val websocketFramerGorgel by Once { req(provided.baseGorgel()).getChild(WebsocketByteIoFramerFactory.WebsocketFramer::class) }
    val methodInvokerCommGorgel by Once { req(provided.baseGorgel()).getChild(MethodInvoker::class).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }
    val constructorInvokerCommGorgel by Once { req(provided.baseGorgel()).getChild(ConstructorInvoker::class).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

    val jsonToObjectDeserializerGorgel by Once { req(provided.baseGorgel()).getChild(JsonToObjectDeserializer::class) }

    val listenerGorgel by Once { req(provided.baseGorgel()).getChild(Listener::class) }

    val objDbLocalGorgel by Once { req(provided.baseGorgel()).getChild(ObjDbLocal::class) }

    val objDbRemoteGorgel by Once { req(provided.baseGorgel()).getChild(ObjDbRemote::class) }

    val odbActorGorgel by Once { req(provided.baseGorgel()).getChild(ObjDbActor::class, COMMUNICATION_CATEGORY_TAG) }

    val providerGorgel by Once { req(provided.baseGorgel()).getChild(Provider::class) }

    val runnerGorgel by Once { req(provided.baseGorgel()).getChild(Runner::class) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceActorCommGorgel by Once { req(serviceActorGorgel).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val sslContextSgdGorgel by Once { req(provided.baseGorgel()).getChild(SslContextSgd::class) }

    val httpSessionConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(HttpSessionConnection::class, COMMUNICATION_CATEGORY_TAG) }
    val rtcpSessionConnectionGorgel by Once { req(provided.baseGorgel()).getChild(RtcpSessionConnection::class) }
    val rtcpSessionConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(RtcpSessionConnection::class, COMMUNICATION_CATEGORY_TAG) }
    val rtcpMessageHandlerCommGorgel by Once { req(provided.baseGorgel()).getChild(RtcpMessageHandler::class, COMMUNICATION_CATEGORY_TAG) }
    val rtcpMessageHandlerFactoryGorgel by Once { req(provided.baseGorgel()).getChild(RtcpMessageHandlerFactory::class) }
    val tcpConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(TcpConnection::class, COMMUNICATION_CATEGORY_TAG) }
    val baseCommGorgel by Once { req(provided.baseGorgel()).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }
    val zeromqThreadCommGorgel by Once { req(provided.baseGorgel()).getChild(ZeromqThread::class, COMMUNICATION_CATEGORY_TAG) }

    val httpMessageHandlerCommGorgel by Once { req(provided.baseGorgel()).getChild(HttpMessageHandler::class, COMMUNICATION_CATEGORY_TAG) }
    val httpMessageHandlerFactoryCommGorgel by Once { req(provided.baseGorgel()).getChild(HttpMessageHandlerFactory::class, COMMUNICATION_CATEGORY_TAG) }

    val inputGorgel by Once { req(provided.baseGorgel()).getChild(ChunkyByteArrayInputStream::class, COMMUNICATION_CATEGORY_TAG) }

    val mustSendDebugReplies by Once { req(provided.props()).testProperty("conf.msgdiagnostics") }

    val selectThreadCommGorgel by Once { req(provided.baseGorgel()).getChild(SelectThread::class, COMMUNICATION_CATEGORY_TAG) }

    val sslContextPropertyNamePrefix by Once { "conf.ssl." }

    val isSslEnabled by Once { req(provided.props()).testProperty("${req(sslContextPropertyNamePrefix)}enable") }

    val optionalSslContext by Once {
        if (req(isSslEnabled))
            req(sslContextSgd.sslContext)
        else
            null
    }

    val listenerFactory by Once {
        ListenerFactory(req(listenerGorgel))
    }

    val tcpConnectionFactory by Once {
        TcpConnectionFactory(
                req(runner),
                req(serverLoadMonitor),
                req(provided.clock()),
                req(tcpConnectionGorgel),
                req(tcpConnectionCommGorgel),
                req(connectionIdGenerator))
    }

    val selectThread by Once {
        SelectThread(
                opt(optionalSslContext),
                req(selectThreadCommGorgel),
                req(tcpConnectionFactory),
                req(listenerFactory))
    }
            .dispose(SelectThread::shutDown)

    val objDbLocalRunnerFactory by Once { ObjDbLocalRunnerFactory(req(runnerGorgel)) }

    val objDbLocalFactory by Once {
        ObjDbLocalFactory(
                req(provided.props()),
                req(objDbLocalGorgel),
                req(objDbLocalRunnerFactory),
                req(provided.baseGorgel()),
                req(jsonToObjectDeserializer),
                req(runner))
    }

    val chunkyByteArrayInputStreamFactory by Once {
        ChunkyByteArrayInputStreamFactory(req(inputGorgel))
    }

    val httpRequestByteIoFramerFactoryFactory by Once {
        HttpRequestByteIoFramerFactoryFactory(req(baseCommGorgel), req(chunkyByteArrayInputStreamFactory))
    }

    val jsonByteIoFramerFactoryFactory by Once {
        JsonByteIoFramerFactoryFactory(req(jsonByteIoFramerWithoutLabelGorgel), req(chunkyByteArrayInputStreamFactory), req(mustSendDebugReplies))
    }

    val rtcpByteIoFramerFactoryFactory by Once {
        RtcpRequestByteIoFramerFactoryFactory(req(tcpConnectionGorgel), req(chunkyByteArrayInputStreamFactory), req(mustSendDebugReplies))
    }

    val websocketByteIoFramerFactoryFactory by Once {
        WebsocketByteIoFramerFactoryFactory(req(websocketFramerGorgel), req(chunkyByteArrayInputStreamFactory), req(jsonByteIoFramerFactoryFactory).create())
    }

    val httpSessionConnectionFactory by Once {
        HttpSessionConnectionFactory(
                req(runner),
                req(serverLoadMonitor),
                req(provided.timer()),
                req(provided.clock()),
                req(httpSessionConnectionCommGorgel),
                req(baseCommGorgel),
                req(sessionIdGenerator),
                req(connectionIdGenerator))
    }

    val httpServerFactory by Once {
        HttpServerFactory(
                req(provided.props()),
                req(provided.timer()),
                req(httpMessageHandlerCommGorgel),
                req(httpMessageHandlerFactoryCommGorgel),
                req(tcpServerFactory),
                req(httpRequestByteIoFramerFactoryFactory),
                req(httpSessionConnectionFactory))
    }

    val httpConnectionSetupFactory by Once {
        HttpConnectionSetupFactory(
                req(provided.props()),
                req(httpServerFactory),
                req(baseConnectionSetupGorgel),
                req(jsonHttpFramerCommGorgel),
                req(mustSendDebugReplies))
    }

    val rtcpSessionConnectionFactory by Once {
        RtcpSessionConnectionFactory(
                req(rtcpSessionConnectionGorgel),
                req(rtcpSessionConnectionCommGorgel),
                req(runner),
                req(serverLoadMonitor),
                req(provided.timer()),
                req(provided.clock()),
                req(sessionIdGenerator),
                req(connectionIdGenerator))
    }

    val rtcpMessageHandlerFactoryFactory by Once {
        RtcpMessageHandlerFactoryFactory(
                req(provided.props()),
                req(provided.timer()),
                req(rtcpMessageHandlerCommGorgel),
                req(rtcpSessionConnectionFactory))
    }

    val rtcpServerFactory by Once {
        RtcpServerFactory(
                req(rtcpMessageHandlerFactoryFactory),
                req(tcpServerFactory),
                req(rtcpByteIoFramerFactoryFactory))
    }

    val rtcpConnectionSetupFactory by Once {
        RtcpConnectionSetupFactory(
                req(provided.props()),
                req(rtcpServerFactory),
                req(baseConnectionSetupGorgel),
                req(rtcpMessageHandlerFactoryGorgel))
    }

    val tcpServerFactory by Once {
        TcpServerFactory(req(selectThread))
    }

    val tcpConnectionSetupFactory by Once {
        TcpConnectionSetupFactory(
                req(provided.props()),
                req(tcpServerFactory),
                req(baseConnectionSetupGorgel),
                req(jsonByteIoFramerFactoryFactory))
    }

    val websocketServerFactory by Once {
        WebsocketServerFactory(
                req(tcpServerFactory),
                req(websocketByteIoFramerFactoryFactory))
    }

    val websocketConnectionSetupFactory by Once {
        WebsocketConnectionSetupFactory(
                req(provided.props()),
                req(websocketServerFactory),
                req(baseConnectionSetupGorgel))
    }

    val zeromqConnectionSetupFactory by Once {
        ZeromqConnectionSetupFactory(
                req(provided.props()),
                req(runner),
                req(serverLoadMonitor),
                req(baseConnectionSetupGorgel),
                req(baseCommGorgel),
                req(zeromqThreadCommGorgel),
                req(connectionIdGenerator),
                req(provided.clock()),
                req(jsonByteIoFramerFactoryFactory))
    }

    val tcpClientFactory by Once {
        TcpClientFactory(req(provided.props()), req(serverLoadMonitor), req(runner), req(selectThread))
    }

    val connectionRetrierFactory by Once {
        ConnectionRetrierFactory(
                req(tcpClientFactory),
                req(provided.timer()),
                req(connectionRetrierWithoutLabelGorgel),
                req(jsonByteIoFramerFactoryFactory))
    }

    val connectionSetupFactoriesByCode by Once {
        mapOf("http" to req(httpConnectionSetupFactory),
                "rtcp" to req(rtcpConnectionSetupFactory),
                "tcp" to req(tcpConnectionSetupFactory),
                "ws" to req(websocketConnectionSetupFactory),
                "zmq" to req(zeromqConnectionSetupFactory))
    }

    val messageDispatcher by Once {
        MessageDispatcher(AlwaysBaseTypeResolver, req(methodInvokerCommGorgel), req(jsonToObjectDeserializer))
    }

    val brokerActorFactory by Once { BrokerActorFactory(req(messageDispatcher), req(brokerActorGorgel), req(mustSendDebugReplies)) }

    val serviceActorFactory by Once { ServiceActorFactory(req(serviceActorGorgel), req(serviceActorCommGorgel), req(mustSendDebugReplies)) }

    val server by Once {
        Server(
                req(provided.props()),
                "director",
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(brokerActorFactory),
                req(serviceActorFactory),
                req(messageDispatcher),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(serverTagGenerator),
                req(serverLoadMonitor),
                req(runner),
                req(objDbRemoteFactory),
                req(objDbLocalFactory),
                req(connectionSetupFactoriesByCode),
                req(connectionRetrierFactory))
    }
            .wire {
                it.registerShutdownWatcher(req(provided.externalShutdownWatcher()))
            }
            .init {
                if (it.startListeners("conf.listen", req(directorServiceFactory)) == 0) {
                    req(bootGorgel).error("no listeners specified")
                }
            }

    val serverLoadMonitor by Once {
        ServerLoadMonitor(
                req(provided.timer()),
                req(provided.clock()),
                req(provided.props()).intProperty("conf.load.time", ServerLoadMonitor.DEFAULT_LOAD_SAMPLE_TIMEOUT) * 1000)
    }
            .init {
                if (req(provided.props()).testProperty("conf.load.log")) {
                    it.registerLoadWatcher(object : LoadWatcher {
                        override fun noteLoadSample(loadFactor: Double) {
                            req(serverLoadMonitorGorgel).d?.run { debug("Load $loadFactor") }
                        }
                    })
                }
            }

    val sessionIdGenerator by Once { RandomIdGenerator(req(sessionIdRandom)) }

    val connectionIdGenerator by Once { LongIdGenerator() }

    val sessionIdRandom by Once(::SecureRandom)
            .init { it.nextBoolean() }

    val jsonToObjectDeserializer by Once {
        JsonToObjectDeserializer(
                req(jsonToObjectDeserializerGorgel),
                req(constructorInvokerCommGorgel),
                req(injectors))
    }

    val clockInjector by Once { ClockInjector(req(provided.clock())) }

    val classspecificGorgelInjector by Once { ClassspecificGorgelInjector(req(provided.baseGorgel())) }

    val baseCommGorgelInjector by Once { BaseCommGorgelInjector(req(baseCommGorgel)) }

    val injectors by Once { listOf(req(clockInjector), req(baseCommGorgelInjector), req(classspecificGorgelInjector)) }

    val runner by Once { ThreadRunner(req(runnerGorgel)) }
            .dispose(ThreadRunner::orderlyShutdown)

    val serverTagGenerator by Once { LongIdGenerator() }

    val messageDispatcherFactory by Once { MessageDispatcherFactory(req(methodInvokerCommGorgel), req(jsonToObjectDeserializer)) }

    val objDbRemoteFactory by Once {
        ObjDbRemoteFactory(
                req(provided.props()),
                req(objDbRemoteGorgel),
                req(odbActorGorgel),
                req(messageDispatcherFactory),
                req(provided.hostDescFromPropertiesFactory()),
                req(jsonToObjectDeserializer),
                req(getRequestFactory),
                req(putRequestFactory),
                req(updateRequestFactory),
                req(queryRequestFactory),
                req(removeRequestFactory),
                req(mustSendDebugReplies),
                req(connectionRetrierFactory))
    }

    val getRequestFactory by Once { GetRequestFactory(req(requestTagGenerator)) }

    val putRequestFactory by Once { PutRequestFactory(req(requestTagGenerator)) }

    val updateRequestFactory by Once { UpdateRequestFactory(req(requestTagGenerator)) }

    val queryRequestFactory by Once { QueryRequestFactory(req(requestTagGenerator)) }

    val removeRequestFactory by Once { RemoveRequestFactory(req(requestTagGenerator)) }

    val requestTagGenerator by Once { LongIdGenerator(1L) }

    val providerLimit by Once { req(provided.props()).intProperty("conf.director.providerlimit", 0) }

    val estimatedLoadIncrement by Once { req(provided.props()).doubleProperty("conf.director.estloadbump", DEFAULT_ESTIMATED_LOAD_INCREMENT) }

    val refTable by Once { RefTable(req(messageDispatcher), req(baseCommGorgel).getChild(RefTable::class)) }

    val director: D<Director> by Once {
        Director(
                req(server),
                req(refTable),
                req(directorGorgel),
                req(baseCommGorgel),
                req(random),
                req(estimatedLoadIncrement),
                req(providerLimit))
    }

    val random by Once(::SecureRandom)

    val adminFactory by Once { AdminFactory(req(director)) }

    val providerFactory by Once { ProviderFactory(req(director), req(providerGorgel), req(ordinalGenerator)) }

    val directorServiceFactory by Once {
        DirectorServiceFactory(
                req(director),
                req(directorActorGorgel),
                req(directorActorCommGorgel),
                req(adminFactory),
                req(providerFactory),
                req(mustSendDebugReplies))
    }

    val ordinalGenerator by Once { LongOrdinalGenerator() }
}
