@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.context

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
import org.elkoserver.foundation.json.MethodInvoker
import org.elkoserver.foundation.json.SlowServiceRunnerInjector
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
import org.elkoserver.foundation.run.singlethreadexecutor.SingleThreadExecutorRunner
import org.elkoserver.foundation.run.threadpoolexecutor.ThreadPoolExecutorSlowServiceRunner
import org.elkoserver.foundation.server.BrokerActor
import org.elkoserver.foundation.server.BrokerActorFactory
import org.elkoserver.foundation.server.ListenerConfigurationFromPropertiesFactory
import org.elkoserver.foundation.server.ListenersStarter
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServerDescriptionFromPropertiesFactory
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
import org.elkoserver.objectdatabase.ObjectDatabase
import org.elkoserver.server.context.DirectorGroup.Companion.DEFAULT_RESERVATION_EXPIRATION_TIMEOUT
import org.elkoserver.server.context.model.Context
import org.elkoserver.server.context.model.Item
import org.elkoserver.server.context.model.User
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.opt
import org.ooverkommelig.req
import java.security.SecureRandom
import java.time.Clock

internal class ContextServerSgd(
    provided: Provided,
    configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()
) : SubGraphDefinition(configuration) {
    interface Provided {
        fun clock(): D<Clock>
        fun props(): D<ElkoProperties>
        fun timer(): D<Timer>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
        fun objectDatabase(): D<ObjectDatabase>
    }

    val sslContextSgd = add(SslContextSgd(object : SslContextSgd.Provided {
        override fun props() = provided.props()
        override fun sslContextSgdGorgel() = sslContextSgdGorgel
        override fun sslContextPropertyNamePrefix() = sslContextPropertyNamePrefix
    }, configuration))

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class) }

    val brokerActorGorgel by Once {
        req(provided.baseGorgel()).getChild(
            BrokerActor::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val contextorGorgel by Once { req(provided.baseGorgel()).getChild(Contextor::class) }

    val contextServiceFactoryGorgel by Once { req(provided.baseGorgel()).getChild(ContextServiceFactory::class) }

    val directorGroupGorgel by Once { req(provided.baseGorgel()).getChild(DirectorGroup::class) }

    val directorActorGorgel by Once {
        req(provided.baseGorgel()).getChild(
            DirectorActor::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }

    val internalActorGorgel by Once { req(provided.baseGorgel()).getChild(InternalActor::class) }

    val internalActorCommGorgel by Once { req(internalActorGorgel).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

    val jsonHttpFramerCommGorgel by Once {
        req(provided.baseGorgel()).getChild(JsonHttpFramer::class).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG)
    }
    val tcpConnectionGorgel by Once { req(provided.baseGorgel()).getChild(TcpConnection::class) }
    val jsonByteIoFramerWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(JsonByteIoFramer::class) }
    val websocketFramerGorgel by Once { req(provided.baseGorgel()).getChild(WebsocketByteIoFramerFactory.WebsocketFramer::class) }
    val methodInvokerCommGorgel by Once {
        req(provided.baseGorgel()).getChild(MethodInvoker::class).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG)
    }
    val constructorInvokerCommGorgel by Once {
        req(provided.baseGorgel()).getChild(ConstructorInvoker::class)
            .withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG)
    }

    val jsonToObjectDeserializerGorgel by Once { req(provided.baseGorgel()).getChild(JsonToObjectDeserializer::class) }

    val listenerGorgel by Once { req(provided.baseGorgel()).getChild(Listener::class) }

    val presencerActorGorgel by Once {
        req(provided.baseGorgel()).getChild(
            PresencerActor::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }

    val presencerGroupGorgel by Once { req(provided.baseGorgel()).getChild(PresencerGroup::class) }

    val reservationGorgel by Once { req(provided.baseGorgel()).getChild(Reservation::class) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val listenersStarterGorgel by Once { req(provided.baseGorgel()).getChild(ListenersStarter::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceActorCommGorgel by Once { req(serviceActorGorgel).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val sessionClientGorgel by Once { req(provided.baseGorgel()).getChild(Session::class, Tag("category", "client")) }

    val sessionCommGorgel by Once { req(provided.baseGorgel()).getChild(Session::class, COMMUNICATION_CATEGORY_TAG) }

    val sslContextSgdGorgel by Once { req(provided.baseGorgel()).getChild(SslContextSgd::class) }

    val staticObjectReceiverGorgel by Once { req(provided.baseGorgel()).getChild(StaticObjectList::class) }

    val userActorGorgel by Once { req(provided.baseGorgel()).getChild(UserActor::class) }

    val userActorCommGorgel by Once { req(userActorGorgel).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

    val contextGorgelWithoutRef by Once { req(provided.baseGorgel()).getChild(Context::class) }

    val itemGorgelWithoutRef by Once { req(provided.baseGorgel()).getChild(Item::class) }

    val userGorgelWithoutRef by Once { req(provided.baseGorgel()).getChild(User::class) }

    val httpSessionConnectionCommGorgel by Once {
        req(provided.baseGorgel()).getChild(
            HttpSessionConnection::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }
    val rtcpSessionConnectionGorgel by Once { req(provided.baseGorgel()).getChild(RtcpSessionConnection::class) }
    val rtcpSessionConnectionCommGorgel by Once {
        req(provided.baseGorgel()).getChild(
            RtcpSessionConnection::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }
    val rtcpMessageHandlerCommGorgel by Once {
        req(provided.baseGorgel()).getChild(
            RtcpMessageHandler::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }
    val rtcpMessageHandlerFactoryGorgel by Once { req(provided.baseGorgel()).getChild(RtcpMessageHandlerFactory::class) }
    val tcpConnectionCommGorgel by Once {
        req(provided.baseGorgel()).getChild(
            TcpConnection::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }
    val baseCommGorgel by Once { req(provided.baseGorgel()).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }
    val zeromqThreadCommGorgel by Once {
        req(provided.baseGorgel()).getChild(
            ZeromqThread::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }

    val httpMessageHandlerCommGorgel by Once {
        req(provided.baseGorgel()).getChild(
            HttpMessageHandler::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }
    val httpMessageHandlerFactoryCommGorgel by Once {
        req(provided.baseGorgel()).getChild(
            HttpMessageHandlerFactory::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }

    val internalActorFactoryFactory by Once {
        InternalActorFactoryFactory(
            req(contextor),
            req(internalActorGorgel),
            req(internalActorCommGorgel),
            req(mustSendDebugReplies)
        )
    }

    val userActorFactoryFactory by Once {
        UserActorFactoryFactory(
            req(contextor),
            req(runner),
            req(userActorGorgel),
            req(userActorCommGorgel),
            req(userGorgelWithoutRef),
            req(provided.timer()),
            req(idGenerator),
            req(mustSendDebugReplies)
        )
    }

    val contextServiceFactory by Once {
        ContextServiceFactory(
            req(contextServiceFactoryGorgel),
            req(internalActorFactoryFactory),
            req(userActorFactoryFactory)
        )
    }
        .init {
            val serverListeners = req(listenersStarter).startListeners("conf.listen", it)
            check(serverListeners.isNotEmpty()) { "no listeners specified" }
            req(server).connectToBrokerIfBrokerSpecified()
            // This must run after the listeners of the server have been started.
            val contextor = req(contextor)
            contextor.registerWithDirectors(req(directors), serverListeners)
            contextor.registerWithPresencers(req(presencers))
        }
        .eager()

    val idGenerator by Once { LongIdGenerator(1L) }

    val inputGorgel by Once {
        req(provided.baseGorgel()).getChild(
            ChunkyByteArrayInputStream::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }

    val mustSendDebugReplies by Once { req(provided.props()).testProperty("conf.msgdiagnostics") }

    val selectThreadCommGorgel by Once {
        req(provided.baseGorgel()).getChild(
            SelectThread::class,
            COMMUNICATION_CATEGORY_TAG
        )
    }

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
            req(connectionIdGenerator)
        )
    }

    val selectThread by Once {
        SelectThread(
            opt(optionalSslContext),
            req(selectThreadCommGorgel),
            req(tcpConnectionFactory),
            req(listenerFactory)
        )
    }
        .dispose(SelectThread::shutDown)

    val chunkyByteArrayInputStreamFactory by Once {
        ChunkyByteArrayInputStreamFactory(req(inputGorgel))
    }

    val httpRequestByteIoFramerFactoryFactory by Once {
        HttpRequestByteIoFramerFactoryFactory(req(baseCommGorgel), req(chunkyByteArrayInputStreamFactory))
    }

    val jsonByteIoFramerFactoryFactory by Once {
        JsonByteIoFramerFactoryFactory(
            req(jsonByteIoFramerWithoutLabelGorgel),
            req(chunkyByteArrayInputStreamFactory),
            req(mustSendDebugReplies)
        )
    }

    val rtcpByteIoFramerFactoryFactory by Once {
        RtcpRequestByteIoFramerFactoryFactory(
            req(tcpConnectionGorgel),
            req(chunkyByteArrayInputStreamFactory),
            req(mustSendDebugReplies)
        )
    }

    val websocketByteIoFramerFactoryFactory by Once {
        WebsocketByteIoFramerFactoryFactory(
            req(websocketFramerGorgel),
            req(chunkyByteArrayInputStreamFactory),
            req(jsonByteIoFramerFactoryFactory).create()
        )
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
            req(connectionIdGenerator)
        )
    }

    val httpServerFactory by Once {
        HttpServerFactory(
            req(provided.props()),
            req(provided.timer()),
            req(httpMessageHandlerCommGorgel),
            req(httpMessageHandlerFactoryCommGorgel),
            req(tcpServerFactory),
            req(httpRequestByteIoFramerFactoryFactory),
            req(httpSessionConnectionFactory)
        )
    }

    val httpConnectionSetupFactory by Once {
        HttpConnectionSetupFactory(
            req(provided.props()),
            req(httpServerFactory),
            req(baseConnectionSetupGorgel),
            req(jsonHttpFramerCommGorgel),
            req(mustSendDebugReplies)
        )
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
            req(connectionIdGenerator)
        )
    }

    val rtcpMessageHandlerFactoryFactory by Once {
        RtcpMessageHandlerFactoryFactory(
            req(provided.props()),
            req(provided.timer()),
            req(rtcpMessageHandlerCommGorgel),
            req(rtcpSessionConnectionFactory)
        )
    }

    val rtcpServerFactory by Once {
        RtcpServerFactory(
            req(rtcpMessageHandlerFactoryFactory),
            req(tcpServerFactory),
            req(rtcpByteIoFramerFactoryFactory)
        )
    }

    val rtcpConnectionSetupFactory by Once {
        RtcpConnectionSetupFactory(
            req(provided.props()),
            req(rtcpServerFactory),
            req(baseConnectionSetupGorgel),
            req(rtcpMessageHandlerFactoryGorgel)
        )
    }

    val tcpServerFactory by Once {
        TcpServerFactory(req(selectThread))
    }

    val tcpConnectionSetupFactory by Once {
        TcpConnectionSetupFactory(
            req(provided.props()),
            req(tcpServerFactory),
            req(baseConnectionSetupGorgel),
            req(jsonByteIoFramerFactoryFactory)
        )
    }

    val websocketServerFactory by Once {
        WebsocketServerFactory(
            req(tcpServerFactory),
            req(websocketByteIoFramerFactoryFactory)
        )
    }

    val websocketConnectionSetupFactory by Once {
        WebsocketConnectionSetupFactory(
            req(provided.props()),
            req(websocketServerFactory),
            req(baseConnectionSetupGorgel)
        )
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
            req(jsonByteIoFramerFactoryFactory)
        )
    }

    val tcpClientFactory by Once {
        TcpClientFactory(req(provided.props()), req(serverLoadMonitor), req(runner), req(selectThread))
    }

    val connectionRetrierFactory by Once {
        ConnectionRetrierFactory(
            req(tcpClientFactory),
            req(provided.timer()),
            req(connectionRetrierWithoutLabelGorgel),
            req(jsonByteIoFramerFactoryFactory)
        )
    }

    val connectionSetupFactoriesByCode by Once {
        mapOf(
            "http" to req(httpConnectionSetupFactory),
            "rtcp" to req(rtcpConnectionSetupFactory),
            "tcp" to req(tcpConnectionSetupFactory),
            "ws" to req(websocketConnectionSetupFactory),
            "zmq" to req(zeromqConnectionSetupFactory)
        )
    }

    val brokerActorFactory by Once {
        BrokerActorFactory(
            req(messageDispatcher),
            req(serverLoadMonitor),
            req(brokerActorGorgel),
            req(mustSendDebugReplies),
            req(provided.externalShutdownWatcher())
        )
    }

    val serviceActorFactory by Once {
        ServiceActorFactory(
            req(serviceActorGorgel),
            req(serviceActorCommGorgel),
            req(mustSendDebugReplies)
        )
    }

    val listenerConfigurationFromPropertiesFactory by Once {
        ListenerConfigurationFromPropertiesFactory(
            req(provided.props()),
            req(provided.authDescFromPropertiesFactory())
        )
    }

    val serverDescriptionFromPropertiesFactory by Once { ServerDescriptionFromPropertiesFactory(req(provided.props())) }

    val serverDescription by Once { req(serverDescriptionFromPropertiesFactory).create("context") }

    val serverName by Once { req(serverDescription).serverName }

    val server by Once {
        Server(
            req(provided.props()),
            req(serverName),
            req(serverGorgel),
            req(serviceLinkGorgel),
            req(brokerActorFactory),
            req(serviceActorFactory),
            req(messageDispatcher),
            req(provided.hostDescFromPropertiesFactory()).fromProperties("conf.broker"),
            req(serverTagGenerator),
            req(connectionRetrierFactory)
        )
    }
        .dispose { it.shutDown() }

    val listenersStarter by Once {
        ListenersStarter(
            req(provided.props()),
            req(serverDescription).serviceName,
            req(listenersStarterGorgel),
            req(listenerConfigurationFromPropertiesFactory),
            req(connectionSetupFactoriesByCode),
            req(server)
        )
    }

    val slowRunnerMaximumNumberOfThreads by Once {
        req(provided.props()).intProperty(
            "conf.slowthreads",
            DEFAULT_SLOW_THREADS
        )
    }

    val slowRunner by Once { ThreadPoolExecutorSlowServiceRunner(req(runner), req(slowRunnerMaximumNumberOfThreads)) }

    val serverLoadMonitor by Once {
        ServerLoadMonitor(
            req(provided.timer()),
            req(provided.clock()),
            req(provided.props()).intProperty("conf.load.time", ServerLoadMonitor.DEFAULT_LOAD_SAMPLE_TIMEOUT) * 1000
        )
    }
        .init {
            if (req(provided.props()).testProperty("conf.load.log")) {
                it.registerLoadWatcher { loadFactor -> req(serverLoadMonitorGorgel).d?.run { debug("Load $loadFactor") } }
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
            req(injectors)
        )
    }

    val clockInjector by Once { ClockInjector(req(provided.clock())) }

    val classspecificGorgelInjector by Once { ClassspecificGorgelInjector(req(provided.baseGorgel())) }

    val baseCommGorgelInjector by Once { BaseCommGorgelInjector(req(baseCommGorgel)) }

    val slowServiceRunnerInjector by Once { SlowServiceRunnerInjector(req(slowRunner)) }

    val injectors by Once {
        listOf(
            req(clockInjector),
            req(baseCommGorgelInjector),
            req(classspecificGorgelInjector),
            req(slowServiceRunnerInjector)
        )
    }

    val runner by Once { SingleThreadExecutorRunner() }
        .dispose(SingleThreadExecutorRunner::orderlyShutdown)

    val serverTagGenerator by Once { LongIdGenerator() }

    val contextorEntryTimeout by Once {
        req(provided.props()).intProperty("conf.context.entrytimeout", DEFAULT_ENTER_TIMEOUT_IN_SECONDS)
    }

    val contextorLimit by Once {
        req(provided.props()).intProperty("conf.context.userlimit", 0)
    }

    val objectDatabaseDispatcher by Once {
        MessageDispatcher(
            req(provided.objectDatabase()),
            req(methodInvokerCommGorgel),
            req(jsonToObjectDeserializer)
        )
    }

    val refTable by Once { RefTable(req(objectDatabaseDispatcher), req(baseCommGorgel).getChild(RefTable::class)) }

    val objectDatabaseClassList by Once {
        mapOf(
            "context" to Context::class.java,
            "item" to Item::class.java,
            "user" to User::class.java,
            "serverdesc" to ServerDesc::class.java,
            "statics" to StaticObjectList::class.java,
        )
    }

    val messageDispatcher by Once {
        // The type resolver used to be "null" for "Contextor". Does the change to "AlwaysBaseTypeResolver" affect the behavior negatively?
        MessageDispatcher(AlwaysBaseTypeResolver, req(methodInvokerCommGorgel), req(jsonToObjectDeserializer))
    }

    val reservationFactory by Once {
        ReservationFactory(req(reservationGorgel), req(reservationTimeout), req(provided.timer()))
    }

    val directorActorFactory by Once {
        DirectorActorFactory(
            req(directorActorGorgel),
            req(reservationFactory),
            req(provided.timer()),
            req(mustSendDebugReplies),
            req(provided.externalShutdownWatcher())
        )
    }

    val presencerGroupFactory by Once {
        PresencerGroupFactory(
            req(server),
            req(presencerGroupGorgel),
            req(presencerActorGorgel),
            req(messageDispatcher),
            req(provided.timer()),
            req(provided.props()),
            req(mustSendDebugReplies),
            req(connectionRetrierFactory)
        )
    }

    val directorGroupFactory by Once {
        DirectorGroupFactory(
            req(server),
            req(serverLoadMonitor),
            req(directorGroupGorgel),
            req(reservationFactory),
            req(directorActorFactory),
            req(messageDispatcher),
            req(provided.timer()),
            req(provided.props()),
            req(connectionRetrierFactory)
        )
    }

    val sessionFactory by Once {
        SessionFactory(
            req(server),
            req(sessionClientGorgel),
            req(sessionCommGorgel),
            opt(sessionPassword),
            req(provided.externalShutdownWatcher())
        )
    }

    val contextor by Once {
        Contextor(
            req(provided.objectDatabase()),
            req(server),
            req(runner),
            req(refTable),
            req(contextorGorgel),
            req(contextGorgelWithoutRef),
            req(itemGorgelWithoutRef),
            req(staticObjectReceiverGorgel),
            req(presencerGroupFactory),
            req(directorGroupFactory),
            req(sessionFactory),
            req(provided.timer()),
            req(contextorEntryTimeout),
            req(contextorLimit),
            req(contextorRandom),
            opt(staticsToLoad),
            opt(families)
        )
    }
        .dispose { it.shutDown() }

    val sessionPassword by Once { req(provided.props()).getPropertyOrNull("conf.context.shutdownpassword") }

    val staticsToLoad by Once { req(provided.props()).getProperty("conf.context.statics") }

    val reservationTimeout by Once {
        1000 * req(provided.props()).intProperty(
            "conf.context.reservationexpire",
            DEFAULT_RESERVATION_EXPIRATION_TIMEOUT
        )
    }

    val families by Once { req(provided.props()).getProperty("conf.context.contexts") }

    val contextorRandom by Once(::SecureRandom)

    val hostListScanner by Once { HostListScanner(req(provided.hostDescFromPropertiesFactory())) }

    val directors by Once { req(hostListScanner).scan("conf.register") }

    val presencers by Once { req(hostListScanner).scan("conf.presence") }

    companion object {
        private const val DEFAULT_ENTER_TIMEOUT_IN_SECONDS = 15

        /** Default value for max number of threads in slow service thread pool.  */
        private const val DEFAULT_SLOW_THREADS = 5
    }
}
