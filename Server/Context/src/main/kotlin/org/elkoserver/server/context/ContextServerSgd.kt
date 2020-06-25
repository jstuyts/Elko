@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.context

import org.elkoserver.foundation.json.ClockInjector
import org.elkoserver.foundation.json.ConstructorInvoker
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MethodInvoker
import org.elkoserver.foundation.json.TraceFactoryInjector
import org.elkoserver.foundation.net.BaseConnectionSetup
import org.elkoserver.foundation.net.ChunkyByteArrayInputStream
import org.elkoserver.foundation.net.JSONByteIOFramer
import org.elkoserver.foundation.net.Listener
import org.elkoserver.foundation.net.SelectThread
import org.elkoserver.foundation.net.SslSetup
import org.elkoserver.foundation.net.TCPConnection
import org.elkoserver.foundation.net.WebSocketByteIOFramerFactory
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrier
import org.elkoserver.foundation.net.http.server.HTTPSessionConnection
import org.elkoserver.foundation.net.http.server.HttpConnectionSetupFactory
import org.elkoserver.foundation.net.http.server.HttpServerFactory
import org.elkoserver.foundation.net.http.server.JSONHTTPFramer
import org.elkoserver.foundation.net.rtcp.server.RTCPSessionConnection
import org.elkoserver.foundation.net.rtcp.server.RtcpConnectionSetupFactory
import org.elkoserver.foundation.net.rtcp.server.RtcpServerFactory
import org.elkoserver.foundation.net.tcp.client.TcpClientFactory
import org.elkoserver.foundation.net.tcp.server.TcpConnectionSetupFactory
import org.elkoserver.foundation.net.tcp.server.TcpServerFactory
import org.elkoserver.foundation.net.ws.server.WebSocketConnectionSetupFactory
import org.elkoserver.foundation.net.ws.server.WebSocketServerFactory
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
import org.elkoserver.objdb.ObjDBRemote
import org.elkoserver.objdb.ObjDBRemoteFactory
import org.elkoserver.objdb.PutRequestFactory
import org.elkoserver.objdb.QueryRequestFactory
import org.elkoserver.objdb.RemoveRequestFactory
import org.elkoserver.objdb.UpdateRequestFactory
import org.elkoserver.server.context.DirectorGroup.Companion.DEFAULT_RESERVATION_EXPIRATION_TIMEOUT
import org.elkoserver.util.trace.TraceFactory
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

internal class ContextServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun clock(): D<Clock>
        fun props(): D<ElkoProperties>
        fun timer(): D<Timer>
        fun traceFactory(): D<TraceFactory>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
    }

    val contTrace by Once { req(provided.traceFactory()).trace("cont") }

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class) }

    val brokerActorGorgel by Once { req(provided.baseGorgel()).getChild(BrokerActor::class, Tag("category", "comm")) }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val contextorGorgel by Once { req(provided.baseGorgel()).getChild(Contextor::class) }

    val contextServiceFactoryGorgel by Once { req(provided.baseGorgel()).getChild(ContextServiceFactory::class) }

    val directorGroupGorgel by Once { req(provided.baseGorgel()).getChild(DirectorGroup::class) }

    val directorActorGorgel by Once { req(provided.baseGorgel()).getChild(DirectorActor::class, Tag("category", "comm")) }

    val internalActorGorgel by Once { req(provided.baseGorgel()).getChild(InternalActor::class) }

    val internalActorCommGorgel by Once { req(internalActorGorgel).withAdditionalStaticTags(Tag("category", "comm")) }

    val jsonHttpFramerCommGorgel by Once { req(provided.baseGorgel()).getChild(JSONHTTPFramer::class).withAdditionalStaticTags(Tag("category", "comm")) }
    val tcpConnectionGorgel by Once { req(provided.baseGorgel()).getChild(TCPConnection::class) }
    val jsonByteIoFramerWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(JSONByteIOFramer::class) }
    val websocketFramerGorgel by Once { req(provided.baseGorgel()).getChild(WebSocketByteIOFramerFactory.WebSocketFramer::class) }
    val methodInvokerCommGorgel by Once { req(provided.baseGorgel()).getChild(MethodInvoker::class).withAdditionalStaticTags(Tag("category", "comm")) }
    val constructorInvokerCommGorgel by Once { req(provided.baseGorgel()).getChild(ConstructorInvoker::class).withAdditionalStaticTags(Tag("category", "comm")) }

    val jsonToObjectDeserializerGorgel by Once { req(provided.baseGorgel()).getChild(JsonToObjectDeserializer::class) }

    val listenerGorgel by Once { req(provided.baseGorgel()).getChild(Listener::class) }

    val objDbLocalGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBLocal::class) }

    val objDbRemoteGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBRemote::class) }

    val odbActorGorgel by Once { req(provided.baseGorgel()).getChild(ODBActor::class, Tag("category", "comm")) }

    val presencerActorGorgel by Once { req(provided.baseGorgel()).getChild(PresencerActor::class, Tag("category", "comm")) }

    val presencerGroupGorgel by Once { req(provided.baseGorgel()).getChild(PresencerGroup::class) }

    val reservationGorgel by Once { req(provided.baseGorgel()).getChild(Reservation::class) }

    val runnerGorgel by Once { req(provided.baseGorgel()).getChild(Runner::class) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceActorCommGorgel by Once { req(serviceActorGorgel).withAdditionalStaticTags(Tag("category", "comm")) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val sessionClientGorgel by Once { req(provided.baseGorgel()).getChild(Session::class, Tag("category", "client")) }

    val staticObjectReceiverGorgel by Once { req(provided.baseGorgel()).getChild(StaticObjectList::class) }

    val userActorGorgel by Once { req(provided.baseGorgel()).getChild(UserActor::class) }

    val userActorCommGorgel by Once { req(userActorGorgel).withAdditionalStaticTags(Tag("category", "comm")) }

    val contextGorgelWithoutRef by Once { req(provided.baseGorgel()).getChild(Context::class) }

    val itemGorgelWithoutRef by Once { req(provided.baseGorgel()).getChild(Item::class) }

    val userGorgelWithoutRef by Once { req(provided.baseGorgel()).getChild(User::class) }

    val httpSessionConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(HTTPSessionConnection::class, Tag("category", "comm")) }
    val rtcpSessionConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(RTCPSessionConnection::class, Tag("category", "comm")) }
    val tcpConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(TCPConnection::class, Tag("category", "comm")) }
    val connectionBaseCommGorgel by Once { req(provided.baseGorgel()).withAdditionalStaticTags(Tag("category", "comm")) }

    val contextServiceFactory by Once {
        ContextServiceFactory(
                req(contextor),
                req(contextServiceFactoryGorgel),
                req(internalActorGorgel),
                req(internalActorCommGorgel),
                req(userActorGorgel),
                req(userActorCommGorgel),
                req(userGorgelWithoutRef),
                req(provided.timer()),
                req(idGenerator),
                req(mustSendDebugReplies))
    }
            .init {
                if (req(server).startListeners("conf.listen", it) == 0) {
                    // FIXME: Do not use "fatalError" as this exits the process hard.
                    throw IllegalStateException("no listeners specified")
                }
                // This must run after the listeners of the server have been started.
                val contextor = req(contextor)
                contextor.registerWithDirectors(req(directors), req(serverListeners))
                contextor.registerWithPresencers(req(presencers))
            }
            .eager()

    val idGenerator by Once { LongIdGenerator(1L) }

    val inputGorgel by Once { req(provided.baseGorgel()).getChild(ChunkyByteArrayInputStream::class, Tag("category", "comm")) }

    val sslSetupGorgel by Once { req(provided.baseGorgel()).getChild(SslSetup::class) }

    val mustSendDebugReplies by Once { req(provided.props()).testProperty("conf.msgdiagnostics") }

    val selectThreadCommGorgel by Once { req(provided.baseGorgel()).getChild(SelectThread::class, Tag("category", "comm")) }

    val sslContext by Once {
        if (req(provided.props()).testProperty("conf.ssl.enable"))
            SslSetup.setupSsl(req(provided.props()), "conf.ssl.", req(sslSetupGorgel))
        else
            null
    }

    val selectThread by Once {
        SelectThread(
                req(runner),
                req(serverLoadMonitor),
                opt(sslContext),
                req(provided.clock()),
                req(selectThreadCommGorgel),
                req(tcpConnectionCommGorgel),
                req(connectionIdGenerator))
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

    val httpServerFactory by Once {
        HttpServerFactory(
                req(provided.props()),
                req(serverLoadMonitor),
                req(runner),
                req(provided.timer()),
                req(provided.clock()),
                req(httpSessionConnectionCommGorgel),
                req(provided.traceFactory()),
                req(inputGorgel),
                req(sessionIdGenerator),
                req(connectionIdGenerator),
                req(tcpServerFactory))
    }

    val httpConnectionSetupFactory by Once {
        HttpConnectionSetupFactory(
                req(provided.props()),
                req(httpServerFactory),
                req(baseConnectionSetupGorgel),
                req(listenerGorgel),
                req(jsonHttpFramerCommGorgel),
                req(provided.traceFactory()),
                req(mustSendDebugReplies))
    }

    val rtcpServerFactory by Once {
        RtcpServerFactory(
                req(provided.props()),
                req(serverLoadMonitor),
                req(runner),
                req(provided.timer()),
                req(provided.clock()),
                req(rtcpSessionConnectionCommGorgel),
                req(provided.traceFactory()),
                req(inputGorgel),
                req(tcpConnectionGorgel),
                req(sessionIdGenerator),
                req(connectionIdGenerator),
                req(mustSendDebugReplies),
                req(tcpServerFactory))
    }

    val rtcpConnectionSetupFactory by Once {
        RtcpConnectionSetupFactory(
                req(provided.props()),
                req(rtcpServerFactory),
                req(baseConnectionSetupGorgel),
                req(listenerGorgel),
                req(provided.traceFactory()))
    }

    val tcpServerFactory by Once {
        TcpServerFactory(req(listenerGorgel), req(selectThread))
    }

    val tcpConnectionSetupFactory by Once {
        TcpConnectionSetupFactory(
                req(provided.props()),
                req(tcpServerFactory),
                req(baseConnectionSetupGorgel),
                req(listenerGorgel),
                req(provided.traceFactory()),
                req(inputGorgel),
                req(jsonByteIoFramerWithoutLabelGorgel),
                req(mustSendDebugReplies))
    }

    val webSocketServerFactory by Once {
        WebSocketServerFactory(
                req(inputGorgel),
                req(jsonByteIoFramerWithoutLabelGorgel),
                req(websocketFramerGorgel),
                req(mustSendDebugReplies),
                req(tcpServerFactory))
    }

    val webSocketConnectionSetupFactory by Once {
        WebSocketConnectionSetupFactory(
                req(provided.props()),
                req(webSocketServerFactory),
                req(baseConnectionSetupGorgel),
                req(listenerGorgel),
                req(provided.traceFactory()))
    }

    val zeromqConnectionSetupFactory by Once {
        ZeromqConnectionSetupFactory(
                req(provided.props()),
                req(runner),
                req(serverLoadMonitor),
                req(baseConnectionSetupGorgel),
                req(listenerGorgel),
                req(connectionBaseCommGorgel),
                req(inputGorgel),
                req(jsonByteIoFramerWithoutLabelGorgel),
                req(provided.traceFactory()),
                req(connectionIdGenerator),
                req(provided.clock()),
                req(mustSendDebugReplies))
    }

    val tcpClientFactory by Once {
        TcpClientFactory(req(provided.props()), req(serverLoadMonitor), req(runner), req(selectThread))
    }

    val server by Once {
        Server(
                req(provided.props()),
                "context",
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(serviceActorGorgel),
                req(serviceActorCommGorgel),
                req(connectionRetrierWithoutLabelGorgel),
                req(jsonByteIoFramerWithoutLabelGorgel),
                req(brokerActorGorgel),
                req(contTrace),
                req(provided.timer()),
                req(inputGorgel),
                req(methodInvokerCommGorgel),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(serverTagGenerator),
                req(serverLoadMonitor),
                req(jsonToObjectDeserializer),
                req(runner),
                req(objDBRemoteFactory),
                req(mustSendDebugReplies),
                req(tcpClientFactory),
                req(objDBLocalFactory),
                req(httpConnectionSetupFactory),
                req(rtcpConnectionSetupFactory),
                req(tcpConnectionSetupFactory),
                req(webSocketConnectionSetupFactory),
                req(zeromqConnectionSetupFactory))
    }
            .wire {
                it.registerShutdownWatcher(req(provided.externalShutdownWatcher()))
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

    val traceFactoryInjector by Once { TraceFactoryInjector(req(provided.traceFactory())) }

    val injectors by Once { listOf(req(clockInjector), req(traceFactoryInjector)) }

    val runner by Once { Runner(req(runnerGorgel)) }
            .dispose { it.orderlyShutdown() }

    val serverTagGenerator by Once { LongIdGenerator() }

    val objDBRemoteFactory by Once {
        ObjDBRemoteFactory(
                req(provided.props()),
                req(objDbRemoteGorgel),
                req(methodInvokerCommGorgel),
                req(connectionRetrierWithoutLabelGorgel),
                req(jsonByteIoFramerWithoutLabelGorgel),
                req(odbActorGorgel),
                req(provided.traceFactory()),
                req(inputGorgel),
                req(provided.timer()),
                req(provided.hostDescFromPropertiesFactory()),
                req(jsonToObjectDeserializer),
                req(getRequestFactory),
                req(putRequestFactory),
                req(updateRequestFactory),
                req(queryRequestFactory),
                req(removeRequestFactory),
                req(mustSendDebugReplies),
                req(tcpClientFactory))
    }

    val getRequestFactory by Once { GetRequestFactory(req(requestTagGenerator)) }

    val putRequestFactory by Once { PutRequestFactory(req(requestTagGenerator)) }

    val updateRequestFactory by Once { UpdateRequestFactory(req(requestTagGenerator)) }

    val queryRequestFactory by Once { QueryRequestFactory(req(requestTagGenerator)) }

    val removeRequestFactory by Once { RemoveRequestFactory(req(requestTagGenerator)) }

    val requestTagGenerator by Once { LongIdGenerator(1L) }

    val serverListeners by Once { req(server).listeners }

    val objectDatabase by Once {
        req(server).openObjectDatabase("conf.context")!!.apply {
            addClass("context", Context::class.java)
            addClass("item", Item::class.java)
            addClass("user", User::class.java)
            addClass("serverdesc", ServerDesc::class.java)
        }
    }

    val contextorEntryTimeout by Once {
        req(provided.props()).intProperty("conf.context.entrytimeout", DEFAULT_ENTER_TIMEOUT_IN_SECONDS)
    }

    val contextorLimit by Once {
        req(provided.props()).intProperty("conf.context.userlimit", 0)
    }

    val contextor by Once {
        Contextor(
                req(objectDatabase),
                req(server),
                req(tcpClientFactory),
                req(contTrace),
                req(contextorGorgel),
                req(contextGorgelWithoutRef),
                req(itemGorgelWithoutRef),
                req(staticObjectReceiverGorgel),
                req(directorGroupGorgel),
                req(presencerGroupGorgel),
                req(presencerActorGorgel),
                req(reservationGorgel),
                req(connectionRetrierWithoutLabelGorgel),
                req(jsonByteIoFramerWithoutLabelGorgel),
                req(directorActorGorgel),
                req(inputGorgel),
                req(sessionClientGorgel),
                req(methodInvokerCommGorgel),
                req(provided.timer()),
                req(provided.traceFactory()),
                req(contextorEntryTimeout),
                req(contextorLimit),
                req(contextorRandom),
                opt(staticsToLoad),
                req(reservationTimeout),
                opt(families),
                opt(sessionPassword),
                req(provided.props()),
                req(jsonToObjectDeserializer),
                req(mustSendDebugReplies))
    }

    val sessionPassword by Once { req(provided.props()).getProperty<String?>("conf.context.shutdownpassword", null) }

    val staticsToLoad by Once { req(provided.props()).getProperty("conf.context.statics") }

    val reservationTimeout by Once { 1000 * req(provided.props()).intProperty("conf.context.reservationexpire", DEFAULT_RESERVATION_EXPIRATION_TIMEOUT) }

    val families by Once { req(provided.props()).getProperty("conf.context.contexts") }

    val contextorRandom by Once { SecureRandom() }

    val hostListScanner by Once { HostListScanner(req(provided.hostDescFromPropertiesFactory())) }

    val directors by Once { req(hostListScanner).scan("conf.register") }

    val presencers by Once { req(hostListScanner).scan("conf.presence") }

    companion object {
        private const val DEFAULT_ENTER_TIMEOUT_IN_SECONDS = 15
    }
}
