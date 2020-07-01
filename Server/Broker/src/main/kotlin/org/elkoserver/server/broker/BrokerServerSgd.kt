@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.broker

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
import org.elkoserver.foundation.json.MethodInvoker
import org.elkoserver.foundation.net.BaseConnectionSetup
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
import org.elkoserver.objdb.ObjDBRemote
import org.elkoserver.objdb.ObjDBRemoteFactory
import org.elkoserver.objdb.PutRequestFactory
import org.elkoserver.objdb.QueryRequestFactory
import org.elkoserver.objdb.RemoveRequestFactory
import org.elkoserver.objdb.UpdateRequestFactory
import org.elkoserver.ordinalgeneration.LongOrdinalGenerator
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.opt
import org.ooverkommelig.req
import java.security.SecureRandom
import java.time.Clock

internal class BrokerServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun timer(): D<Timer>
        fun props(): D<ElkoProperties>
        fun clock(): D<Clock>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
    }

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class) }

    val brokerBootGorgel by Once { req(provided.baseGorgel()).getChild(BrokerBoot::class) }

    val brokerGorgel by Once { req(provided.baseGorgel()).getChild(Broker::class) }

    val brokerActorGorgel by Once { req(provided.baseGorgel()).getChild(BrokerActor::class) }

    val brokerActorCommGorgel by Once { req(brokerActorGorgel).withAdditionalStaticTags(Tag("category", "comm")) }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val jsonHttpFramerCommGorgel by Once { req(provided.baseGorgel()).getChild(JSONHTTPFramer::class).withAdditionalStaticTags(Tag("category", "comm")) }
    val tcpConnectionGorgel by Once { req(provided.baseGorgel()).getChild(TCPConnection::class) }
    val jsonByteIoFramerWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(JSONByteIOFramer::class) }
    val websocketFramerGorgel by Once { req(provided.baseGorgel()).getChild(WebsocketByteIOFramerFactory.WebsocketFramer::class) }
    val methodInvokerCommGorgel by Once { req(provided.baseGorgel()).getChild(MethodInvoker::class).withAdditionalStaticTags(Tag("category", "comm")) }
    val constructorInvokerCommGorgel by Once { req(provided.baseGorgel()).getChild(ConstructorInvoker::class).withAdditionalStaticTags(Tag("category", "comm")) }

    val jsonToObjectDeserializerGorgel by Once { req(provided.baseGorgel()).getChild(JsonToObjectDeserializer::class) }

    val launcherTableGorgel by Once { req(provided.baseGorgel()).getChild(LauncherTable::class) }

    val listenerGorgel by Once { req(provided.baseGorgel()).getChild(Listener::class) }

    val objDbLocalGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBLocal::class) }

    val objDbRemoteGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBRemote::class) }

    val odbActorGorgel by Once { req(provided.baseGorgel()).getChild(ODBActor::class, Tag("category", "comm")) }

    val runnerGorgel by Once { req(provided.baseGorgel()).getChild(Runner::class) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceActorCommGorgel by Once { req(serviceActorGorgel).withAdditionalStaticTags(Tag("category", "comm")) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val httpSessionConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(HTTPSessionConnection::class, Tag("category", "comm")) }
    val rtcpSessionConnectionGorgel by Once { req(provided.baseGorgel()).getChild(RTCPSessionConnection::class) }
    val rtcpSessionConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(RTCPSessionConnection::class, Tag("category", "comm")) }
    val rtcpMessageHandlerCommGorgel by Once { req(provided.baseGorgel()).getChild(RTCPMessageHandler::class, Tag("category", "comm")) }
    val rtcpMessageHandlerFactoryGorgel by Once { req(provided.baseGorgel()).getChild(RTCPMessageHandlerFactory::class) }
    val tcpConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(TCPConnection::class, Tag("category", "comm")) }
    val baseCommGorgel by Once { req(provided.baseGorgel()).withAdditionalStaticTags(Tag("category", "comm")) }
    val zeromqThreadCommGorgel by Once { req(provided.baseGorgel()).getChild(ZeroMQThread::class, Tag("category", "comm")) }

    val httpMessageHandlerCommGorgel by Once { req(provided.baseGorgel()).getChild(HTTPMessageHandler::class, Tag("category", "comm")) }
    val httpMessageHandlerFactoryCommGorgel by Once { req(provided.baseGorgel()).getChild(HTTPMessageHandlerFactory::class, Tag("category", "comm")) }

    val inputGorgel by Once { req(provided.baseGorgel()).getChild(ChunkyByteArrayInputStream::class, Tag("category", "comm")) }

    val sslSetupGorgel by Once { req(provided.baseGorgel()).getChild(SslSetup::class) }

    val selectThreadCommGorgel by Once { req(provided.baseGorgel()).getChild(SelectThread::class, Tag("category", "comm")) }

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

    val objDBLocalFactory by Once {
        ObjDBLocalFactory(
                req(provided.props()),
                req(objDbLocalGorgel),
                req(runnerGorgel),
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

    val server by Once {
        Server(
                req(provided.props()),
                "broker",
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(serviceActorGorgel),
                req(serviceActorCommGorgel),
                req(brokerActorGorgel),
                req(methodInvokerCommGorgel),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(serverTagGenerator),
                req(serverLoadMonitor),
                req(jsonToObjectDeserializer),
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

    val mustSendDebugReplies by Once { req(provided.props()).testProperty("conf.msgdiagnostics") }

    val objDBRemoteFactory by Once {
        ObjDBRemoteFactory(
                req(provided.props()),
                req(objDbRemoteGorgel),
                req(methodInvokerCommGorgel),
                req(odbActorGorgel),
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

    val refTable by Once { RefTable(AlwaysBaseTypeResolver, req(methodInvokerCommGorgel), req(baseCommGorgel).getChild(RefTable::class), req(jsonToObjectDeserializer))  }

    val broker: D<Broker> by Once {
        Broker(
                req(server),
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
