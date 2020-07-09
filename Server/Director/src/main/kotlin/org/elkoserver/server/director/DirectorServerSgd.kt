@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.director

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStream
import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStreamFactory
import org.elkoserver.foundation.byteioframer.http.HTTPRequestByteIOFramerFactoryFactory
import org.elkoserver.foundation.byteioframer.json.JSONByteIOFramer
import org.elkoserver.foundation.byteioframer.json.JSONByteIOFramerFactoryFactory
import org.elkoserver.foundation.byteioframer.rtcp.RTCPRequestByteIOFramerFactoryFactory
import org.elkoserver.foundation.byteioframer.websocket.WebsocketByteIOFramerFactory
import org.elkoserver.foundation.byteioframer.websocket.WebsocketByteIOFramerFactoryFactory
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
import org.elkoserver.foundation.net.SslSetup
import org.elkoserver.foundation.net.TCPConnection
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrier
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.net.http.server.HTTPMessageHandler
import org.elkoserver.foundation.net.http.server.HTTPMessageHandlerFactory
import org.elkoserver.foundation.net.http.server.HTTPSessionConnection
import org.elkoserver.foundation.net.http.server.HttpConnectionSetupFactory
import org.elkoserver.foundation.net.http.server.HttpServerFactory
import org.elkoserver.foundation.net.http.server.JSONHTTPFramer
import org.elkoserver.foundation.net.rtcp.server.RTCPMessageHandler
import org.elkoserver.foundation.net.rtcp.server.RTCPMessageHandlerFactory
import org.elkoserver.foundation.net.rtcp.server.RTCPSessionConnection
import org.elkoserver.foundation.net.rtcp.server.RtcpConnectionSetupFactory
import org.elkoserver.foundation.net.rtcp.server.RtcpServerFactory
import org.elkoserver.foundation.net.tcp.client.TcpClientFactory
import org.elkoserver.foundation.net.tcp.server.TcpConnectionSetupFactory
import org.elkoserver.foundation.net.tcp.server.TcpServerFactory
import org.elkoserver.foundation.net.ws.server.WebsocketConnectionSetupFactory
import org.elkoserver.foundation.net.ws.server.WebsocketServerFactory
import org.elkoserver.foundation.net.zmq.server.ZeroMQThread
import org.elkoserver.foundation.net.zmq.server.ZeromqConnectionSetupFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.foundation.server.BrokerActor
import org.elkoserver.foundation.server.LoadWatcher
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServerLoadMonitor
import org.elkoserver.foundation.server.ServiceActor
import org.elkoserver.foundation.server.ServiceLink
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.LongIdGenerator
import org.elkoserver.idgeneration.RandomIdGenerator
import org.elkoserver.objdb.GetRequestFactory
import org.elkoserver.objdb.ODBActor
import org.elkoserver.objdb.ObjDBLocal
import org.elkoserver.objdb.ObjDBLocalFactory
import org.elkoserver.objdb.ObjDBLocalRunnerFactory
import org.elkoserver.objdb.ObjDBRemote
import org.elkoserver.objdb.ObjDBRemoteFactory
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
import org.ooverkommelig.req
import java.security.SecureRandom
import java.time.Clock

internal class DirectorServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun timer(): D<Timer>
        fun clock(): D<Clock>
        fun props(): D<ElkoProperties>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
    }

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class) }

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(DirectorBoot::class) }

    val brokerActorGorgel by Once { req(provided.baseGorgel()).getChild(BrokerActor::class, COMMUNICATION_CATEGORY_TAG) }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val directorGorgel by Once { req(provided.baseGorgel()).getChild(Director::class) }

    val directorActorGorgel by Once { req(provided.baseGorgel()).getChild(DirectorActor::class, COMMUNICATION_CATEGORY_TAG) }

    val directorActorCommGorgel by Once { req(directorActorGorgel).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

    val jsonHttpFramerCommGorgel by Once { req(provided.baseGorgel()).getChild(JSONHTTPFramer::class).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }
    val tcpConnectionGorgel by Once { req(provided.baseGorgel()).getChild(TCPConnection::class) }
    val jsonByteIoFramerWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(JSONByteIOFramer::class) }
    val websocketFramerGorgel by Once { req(provided.baseGorgel()).getChild(WebsocketByteIOFramerFactory.WebsocketFramer::class) }
    val methodInvokerCommGorgel by Once { req(provided.baseGorgel()).getChild(MethodInvoker::class).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }
    val constructorInvokerCommGorgel by Once { req(provided.baseGorgel()).getChild(ConstructorInvoker::class).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

    val jsonToObjectDeserializerGorgel by Once { req(provided.baseGorgel()).getChild(JsonToObjectDeserializer::class) }

    val listenerGorgel by Once { req(provided.baseGorgel()).getChild(Listener::class) }

    val objDbLocalGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBLocal::class) }

    val objDbRemoteGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBRemote::class) }

    val odbActorGorgel by Once { req(provided.baseGorgel()).getChild(ODBActor::class, COMMUNICATION_CATEGORY_TAG) }

    val providerGorgel by Once { req(provided.baseGorgel()).getChild(Provider::class) }

    val runnerGorgel by Once { req(provided.baseGorgel()).getChild(Runner::class) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceActorCommGorgel by Once { req(serviceActorGorgel).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val httpSessionConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(HTTPSessionConnection::class, COMMUNICATION_CATEGORY_TAG) }
    val rtcpSessionConnectionGorgel by Once { req(provided.baseGorgel()).getChild(RTCPSessionConnection::class) }
    val rtcpSessionConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(RTCPSessionConnection::class, COMMUNICATION_CATEGORY_TAG) }
    val rtcpMessageHandlerCommGorgel by Once { req(provided.baseGorgel()).getChild(RTCPMessageHandler::class, COMMUNICATION_CATEGORY_TAG) }
    val rtcpMessageHandlerFactoryGorgel by Once { req(provided.baseGorgel()).getChild(RTCPMessageHandlerFactory::class) }
    val tcpConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(TCPConnection::class, COMMUNICATION_CATEGORY_TAG) }
    val baseCommGorgel by Once { req(provided.baseGorgel()).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }
    val zeromqThreadCommGorgel by Once { req(provided.baseGorgel()).getChild(ZeroMQThread::class, COMMUNICATION_CATEGORY_TAG) }

    val httpMessageHandlerCommGorgel by Once { req(provided.baseGorgel()).getChild(HTTPMessageHandler::class, COMMUNICATION_CATEGORY_TAG) }
    val httpMessageHandlerFactoryCommGorgel by Once { req(provided.baseGorgel()).getChild(HTTPMessageHandlerFactory::class, COMMUNICATION_CATEGORY_TAG) }

    val inputGorgel by Once { req(provided.baseGorgel()).getChild(ChunkyByteArrayInputStream::class, COMMUNICATION_CATEGORY_TAG) }

    val sslSetupGorgel by Once { req(provided.baseGorgel()).getChild(SslSetup::class) }

    val mustSendDebugReplies by Once { req(provided.props()).testProperty("conf.msgdiagnostics") }

    val selectThreadCommGorgel by Once { req(provided.baseGorgel()).getChild(SelectThread::class, COMMUNICATION_CATEGORY_TAG) }

    val sslContext by Once {
        if (req(provided.props()).testProperty("conf.ssl.enable"))
            SslSetup.setupSsl(req(provided.props()), "conf.ssl.", req(sslSetupGorgel))
        else
            null
    }

    val listenerFactory by Once {
        ListenerFactory(req(listenerGorgel))
    }

    val selectThread by Once {
        SelectThread(
                req(runner),
                req(serverLoadMonitor),
                opt(sslContext),
                req(provided.clock()),
                req(selectThreadCommGorgel),
                req(tcpConnectionGorgel),
                req(tcpConnectionCommGorgel),
                req(connectionIdGenerator),
                req(listenerFactory))
    }
            .dispose { it.shutDown() }

    val objDBLocalRunnerFactory by Once { ObjDBLocalRunnerFactory(req(runnerGorgel)) }

    val objDBLocalFactory by Once {
        ObjDBLocalFactory(
                req(provided.props()),
                req(objDbLocalGorgel),
                req(objDBLocalRunnerFactory),
                req(provided.baseGorgel()),
                req(jsonToObjectDeserializer),
                req(runner))
    }

    val chunkyByteArrayInputStreamFactory by Once {
        ChunkyByteArrayInputStreamFactory(req(inputGorgel))
    }

    val httpRequestByteIOFramerFactoryFactory by Once {
        HTTPRequestByteIOFramerFactoryFactory(req(baseCommGorgel), req(chunkyByteArrayInputStreamFactory))
    }

    val jsonByteIOFramerFactoryFactory by Once {
        JSONByteIOFramerFactoryFactory(req(jsonByteIoFramerWithoutLabelGorgel), req(chunkyByteArrayInputStreamFactory), req(mustSendDebugReplies))
    }

    val rtcpByteIOFramerFactoryFactory by Once {
        RTCPRequestByteIOFramerFactoryFactory(req(tcpConnectionGorgel), req(chunkyByteArrayInputStreamFactory), req(mustSendDebugReplies))
    }

    val websocketByteIOFramerFactoryFactory by Once {
        WebsocketByteIOFramerFactoryFactory(req(websocketFramerGorgel), req(chunkyByteArrayInputStreamFactory), req(jsonByteIOFramerFactoryFactory).create())
    }

    val httpServerFactory by Once {
        HttpServerFactory(
                req(provided.props()),
                req(serverLoadMonitor),
                req(runner),
                req(provided.timer()),
                req(provided.clock()),
                req(httpSessionConnectionCommGorgel),
                req(baseCommGorgel),
                req(httpMessageHandlerCommGorgel),
                req(httpMessageHandlerFactoryCommGorgel),
                req(sessionIdGenerator),
                req(connectionIdGenerator),
                req(tcpServerFactory),
                req(httpRequestByteIOFramerFactoryFactory))
    }

    val httpConnectionSetupFactory by Once {
        HttpConnectionSetupFactory(
                req(provided.props()),
                req(httpServerFactory),
                req(baseConnectionSetupGorgel),
                req(jsonHttpFramerCommGorgel),
                req(mustSendDebugReplies))
    }

    val rtcpServerFactory by Once {
        RtcpServerFactory(
                req(provided.props()),
                req(serverLoadMonitor),
                req(runner),
                req(provided.timer()),
                req(provided.clock()),
                req(rtcpSessionConnectionGorgel),
                req(rtcpSessionConnectionCommGorgel),
                req(rtcpMessageHandlerCommGorgel),
                req(sessionIdGenerator),
                req(connectionIdGenerator),
                req(tcpServerFactory),
                req(rtcpByteIOFramerFactoryFactory))
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
                req(jsonByteIOFramerFactoryFactory))
    }

    val websocketServerFactory by Once {
        WebsocketServerFactory(
                req(tcpServerFactory),
                req(websocketByteIOFramerFactoryFactory))
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
                req(jsonByteIOFramerFactoryFactory))
    }

    val tcpClientFactory by Once {
        TcpClientFactory(req(provided.props()), req(serverLoadMonitor), req(runner), req(selectThread))
    }

    val connectionRetrierFactory by Once {
        ConnectionRetrierFactory(
                req(tcpClientFactory),
                req(provided.timer()),
                req(connectionRetrierWithoutLabelGorgel),
                req(jsonByteIOFramerFactoryFactory))
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

    val server by Once {
        Server(
                req(provided.props()),
                "director",
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(serviceActorGorgel),
                req(serviceActorCommGorgel),
                req(brokerActorGorgel),
                req(messageDispatcher),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(serverTagGenerator),
                req(serverLoadMonitor),
                req(runner),
                req(objDBRemoteFactory),
                req(mustSendDebugReplies),
                req(objDBLocalFactory),
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

    val sessionIdRandom by Once { SecureRandom() }
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

    val runner by Once { Runner(req(runnerGorgel)) }
            .dispose { it.orderlyShutdown() }

    val serverTagGenerator by Once { LongIdGenerator() }

    val messageDispatcherFactory by Once { MessageDispatcherFactory(req(methodInvokerCommGorgel), req(jsonToObjectDeserializer)) }

    val objDBRemoteFactory by Once {
        ObjDBRemoteFactory(
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

    val random by Once { SecureRandom() }

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
