@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.broker

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStream
import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStreamFactory
import org.elkoserver.foundation.byteioframer.http.HttpRequestByteIoFramerFactoryFactory
import org.elkoserver.foundation.byteioframer.json.JsonByteIoFramer
import org.elkoserver.foundation.byteioframer.json.JsonByteIoFramerFactoryFactory
import org.elkoserver.foundation.byteioframer.rtcp.RtcpRequestByteIoFramerFactoryFactory
import org.elkoserver.foundation.byteioframer.websocket.WebsocketByteIoFramerFactory
import org.elkoserver.foundation.byteioframer.websocket.WebsocketByteIoFramerFactoryFactory
import org.elkoserver.foundation.json.*
import org.elkoserver.foundation.net.*
import org.elkoserver.foundation.net.Communication.COMMUNICATION_CATEGORY_TAG
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrier
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.net.http.server.*
import org.elkoserver.foundation.net.rtcp.server.*
import org.elkoserver.foundation.net.tcp.client.TcpClientFactory
import org.elkoserver.foundation.net.tcp.server.TcpConnectionSetupFactory
import org.elkoserver.foundation.net.tcp.server.TcpServerFactory
import org.elkoserver.foundation.net.ws.server.WebsocketConnectionSetupFactory
import org.elkoserver.foundation.net.ws.server.WebsocketServerFactory
import org.elkoserver.foundation.net.zmq.server.ZeromqConnectionSetupFactory
import org.elkoserver.foundation.net.zmq.server.ZeromqThread
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.singlethreadexecutor.SingleThreadExecutorRunner
import org.elkoserver.foundation.server.*
import org.elkoserver.foundation.server.BrokerActorFactory
import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.LongIdGenerator
import org.elkoserver.idgeneration.RandomIdGenerator
import org.elkoserver.objectdatabase.*
import org.elkoserver.ordinalgeneration.LongOrdinalGenerator
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.*
import java.security.SecureRandom
import java.time.Clock

internal class BrokerServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(configuration) {
    interface Provided {
        fun timer(): D<Timer>
        fun props(): D<ElkoProperties>
        fun clock(): D<Clock>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
    }

    val sslContextSgd = add(SslContextSgd(object : SslContextSgd.Provided {
        override fun props() = provided.props()
        override fun sslContextSgdGorgel() = sslContextSgdGorgel
        override fun sslContextPropertyNamePrefix() = sslContextPropertyNamePrefix
    }, configuration))

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class) }

    val brokerBootGorgel by Once { req(provided.baseGorgel()).getChild(BrokerBoot::class) }

    val brokerGorgel by Once { req(provided.baseGorgel()).getChild(Broker::class) }

    val brokerActorGorgel by Once { req(provided.baseGorgel()).getChild(BrokerActor::class) }

    val brokerActorCommGorgel by Once { req(brokerActorGorgel).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val jsonHttpFramerCommGorgel by Once { req(provided.baseGorgel()).getChild(JsonHttpFramer::class).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }
    val tcpConnectionGorgel by Once { req(provided.baseGorgel()).getChild(TcpConnection::class) }
    val jsonByteIoFramerWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(JsonByteIoFramer::class) }
    val websocketFramerGorgel by Once { req(provided.baseGorgel()).getChild(WebsocketByteIoFramerFactory.WebsocketFramer::class) }
    val methodInvokerCommGorgel by Once { req(provided.baseGorgel()).getChild(MethodInvoker::class).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }
    val constructorInvokerCommGorgel by Once { req(provided.baseGorgel()).getChild(ConstructorInvoker::class).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

    val jsonToObjectDeserializerGorgel by Once { req(provided.baseGorgel()).getChild(JsonToObjectDeserializer::class) }

    val launcherTableGorgel by Once { req(provided.baseGorgel()).getChild(LauncherTable::class) }

    val listenerGorgel by Once { req(provided.baseGorgel()).getChild(Listener::class) }

    val directObjectDatabaseGorgel by Once { req(provided.baseGorgel()).getChild(ObjectDatabaseDirect::class) }

    val repositoryObjectDatabaseGorgel by Once { req(provided.baseGorgel()).getChild(ObjectDatabaseRepository::class) }

    val odbActorGorgel by Once { req(provided.baseGorgel()).getChild(ObjectDatabaseActor::class, COMMUNICATION_CATEGORY_TAG) }

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

    val directObjectDatabaseRunnerFactory by Once { DirectObjectDatabaseRunnerFactory() }

    val directObjectDatabaseFactory by Once {
        DirectObjectDatabaseFactory(
                req(provided.props()),
                req(directObjectDatabaseGorgel),
                req(directObjectDatabaseRunnerFactory),
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

    val brokerActorFactory by Once { BrokerActorFactory(req(messageDispatcher), req(serverLoadMonitor), req(brokerActorGorgel), req(mustSendDebugReplies)) }

    val serviceActorFactory by Once { ServiceActorFactory(req(serviceActorGorgel), req(serviceActorCommGorgel), req(mustSendDebugReplies)) }

    val listenerConfigurationFromPropertiesFactory by Once { ListenerConfigurationFromPropertiesFactory(req(provided.props()), req(provided.authDescFromPropertiesFactory())) }

    val serverDescriptionFromPropertiesFactory by Once { ServerDescriptionFromPropertiesFactory(req(provided.props())) }

    val serverDescription by Once { req(serverDescriptionFromPropertiesFactory).create("broker") }

    val server by Once {
        Server(
                req(provided.props()),
                req(serverDescription),
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(brokerActorFactory),
                req(serviceActorFactory),
                req(messageDispatcher),
                req(listenerConfigurationFromPropertiesFactory),
                req(provided.hostDescFromPropertiesFactory()).fromProperties("conf.broker"),
                req(serverTagGenerator),
                req(connectionSetupFactoriesByCode),
                req(connectionRetrierFactory))
    }
            .wire {
                it.registerShutdownWatcher(req(provided.externalShutdownWatcher()))
            }
            .init {
                if (it.startListeners("conf.listen", req(brokerServiceFactory)) == 0) {
                    req(brokerBootGorgel).error("no listeners specified")
                }
                for (service in it.services()) {
                    service.providerID = 0
                    req(broker).addService(service)
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

    val runner by Once { SingleThreadExecutorRunner() }
            .dispose(SingleThreadExecutorRunner::orderlyShutdown)

    val serverTagGenerator by Once { LongIdGenerator() }

    val mustSendDebugReplies by Once { req(provided.props()).testProperty("conf.msgdiagnostics") }

    val messageDispatcherFactory by Once { MessageDispatcherFactory(req(methodInvokerCommGorgel), req(jsonToObjectDeserializer)) }

    val repositoryObjectDatabaseFactory by Once {
        RepositoryObjectDatabaseFactory(
                req(server),
                req(serverDescription).serverName,
                req(provided.props()),
                req(repositoryObjectDatabaseGorgel),
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

    val startMode by Once {
        when (val startModeStr = req(provided.props()).getProperty("conf.broker.startmode")) {
            null, "initial" -> LauncherTable.START_INITIAL
            "recover" -> LauncherTable.START_RECOVER
            "restart" -> LauncherTable.START_RESTART
            else -> {
                // FIXME: Use another Gorgel
                req(brokerGorgel).error("unknown startmode value '$startModeStr'")
                LauncherTable.START_RECOVER
            }
        }
    }

    val refTable by Once { RefTable(req(messageDispatcher), req(baseCommGorgel).getChild(RefTable::class)) }

    val objectDatabaseFactory by Once { ObjectDatabaseFactory(req(provided.props()), req(repositoryObjectDatabaseFactory), req(directObjectDatabaseFactory)) }

    val broker: D<Broker> by Once {
        Broker(
                req(server),
                req(objectDatabaseFactory),
                req(refTable),
                req(brokerGorgel),
                req(launcherTableGorgel),
                req(provided.timer()),
                req(baseCommGorgel),
                req(startMode))
    }

    val brokerServiceFactory by Once {
        BrokerServiceFactory(
                req(broker),
                req(brokerActorGorgel),
                req(brokerActorCommGorgel),
                req(clientOrdinalGenerator),
                req(mustSendDebugReplies))
    }

    val clientOrdinalGenerator by Once { LongOrdinalGenerator() }
}
