@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.gatekeeper

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
import org.elkoserver.foundation.json.RandomInjector
import org.elkoserver.foundation.net.BaseConnectionSetup
import org.elkoserver.foundation.net.Communication.COMMUNICATION_CATEGORY_TAG
import org.elkoserver.foundation.net.Listener
import org.elkoserver.foundation.net.ListenerFactory
import org.elkoserver.foundation.net.SelectThread
import org.elkoserver.foundation.net.SslSetup
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
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedAdministration
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.opt
import org.ooverkommelig.req
import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.time.Clock

internal class GatekeeperServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun timer(): D<Timer>
        fun clock(): D<Clock>
        fun props(): D<ElkoProperties>
        fun baseGorgel(): D<Gorgel>
        fun baseCommGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
    }

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class) }

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(GatekeeperBoot::class) }

    val brokerActorGorgel by Once { req(provided.baseGorgel()).getChild(BrokerActor::class, COMMUNICATION_CATEGORY_TAG) }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val directorActorFactoryGorgel by Once { req(provided.baseGorgel()).getChild(DirectorActorFactory::class) }

    val directorActorGorgel by Once { req(provided.baseGorgel()).getChild(DirectorActor::class, COMMUNICATION_CATEGORY_TAG) }

    val gatekeeperGorgel by Once { req(provided.baseGorgel()).getChild(Gatekeeper::class) }

    val gatekeeperActorGorgel by Once { req(provided.baseGorgel()).getChild(GatekeeperActor::class) }

    val gatekeeperActorCommGorgel by Once { req(gatekeeperActorGorgel).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

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

    val runnerGorgel by Once { req(provided.baseGorgel()).getChild(Runner::class) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceActorCommGorgel by Once { req(serviceActorGorgel).withAdditionalStaticTags(COMMUNICATION_CATEGORY_TAG) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

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
                opt(sslContext),
                req(selectThreadCommGorgel),
                req(tcpConnectionFactory),
                req(listenerFactory))
    }
            .dispose { it.shutDown() }

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

    val brokerActorFactory by Once { BrokerActorFactory(req(messageDispatcher), req(brokerActorGorgel), req(mustSendDebugReplies)) }

    val serviceActorFactory by Once { ServiceActorFactory(req(serviceActorGorgel), req(serviceActorCommGorgel), req(mustSendDebugReplies)) }

    val server by Once {
        Server(
                req(provided.props()),
                "gatekeeper",
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
                if (it.startListeners("conf.listen", req(gatekeeperServiceFactory)) == 0) {
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

    val deserializedObjectRandom by Once { SecureRandom() }

    val deserializedObjectRandomInjector by Once { RandomInjector(req(deserializedObjectRandom)) }

    val deserializedObjectMessageDigest by Once {
        try {
            MessageDigest.getInstance("SHA")
        } catch (e: NoSuchAlgorithmException) {
            /* According to Sun's documentation, this exception can't actually
           happen, since the JVM is required to support the SHA algorithm.
           However, the compiler requires the catch.  And it *could* happen
           if either the documentation or the JVM implementation are wrong.
           Like that ever happens. */
            throw IllegalStateException("This JVM lacks SHA support", e)
        }
    }

    val deserializedObjectMessageDigestInjector by Once { MessageDigestInjector(req(deserializedObjectMessageDigest)) }

    val injectors by Once { listOf(req(clockInjector), req(deserializedObjectRandomInjector), req(deserializedObjectMessageDigestInjector), req(baseCommGorgelInjector), req(classspecificGorgelInjector)) }

    val runner by Once { Runner(req(runnerGorgel)) }
            .dispose { it.orderlyShutdown() }

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

    val refTable by Once { RefTable(req(messageDispatcher), req(baseCommGorgel).getChild(RefTable::class)) }

    val messageDispatcher by Once {
        // The type resolver used to be "null" for "Gatekeeper" and "RefTable". Does the change to "AlwaysBaseTypeResolver" affect the behavior negatively?
        MessageDispatcher(AlwaysBaseTypeResolver, req(methodInvokerCommGorgel), req(jsonToObjectDeserializer))
    }

    val directorActorFactoryFactory by Once {
        DirectorActorFactoryFactory(
                req(directorActorFactoryGorgel),
                req(directorActorGorgel),
                req(messageDispatcher),
                req(mustSendDebugReplies),
                req(connectionRetrierFactory))
    }

    val gatekeeper: D<Gatekeeper> by Once {
        Gatekeeper(
                req(server),
                req(refTable),
                req(gatekeeperGorgel),
                req(baseCommGorgel),
                req(directorActorFactoryFactory),
                req(provided.hostDescFromPropertiesFactory()),
                req(provided.props()))
    }

    val actionTimeout by Once { 1000 * req(provided.props()).intProperty("conf.gatekeeper.actiontimeout", GatekeeperBoot.DEFAULT_ACTION_TIMEOUT) }

    val gatekeeperServiceFactory by Once { GatekeeperServiceFactory(req(gatekeeper), req(actionTimeout), req(gatekeeperActorGorgel), req(provided.timer()), req(gatekeeperActorCommGorgel), req(mustSendDebugReplies)) }

    val authorizerOgdClassName by Once {
        req(provided.props()).getProperty("conf.gatekeeper.authorizer",
                "org.elkoserver.server.gatekeeper.passwd.PasswdAuthorizerOgd")
    }

    val authorizerOgdClass by Once {
        try {
            Class.forName(req(authorizerOgdClassName))
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("auth service class ${req(authorizerOgdClassName)} not found", e)
        }
    }

    val authorizerOgd by Once {
        try {
            req(authorizerOgdClass).getConstructor(AuthorizerProvided::class.java, ObjectGraphConfiguration::class.java).newInstance(object : ProvidedAdministration(), AuthorizerProvided {
                override fun props() = provided.props()
                override fun gatekeeper() = gatekeeper
                override fun server() = server
                override fun baseGorgel() = provided.baseGorgel()
                override fun baseCommGorgel() = provided.baseCommGorgel()
            }, configuration) as AuthorizerOgd
        } catch (e: IllegalAccessException) {
            throw IllegalStateException("unable to access auth service constructor", e)
        } catch (e: InstantiationException) {
            throw IllegalStateException("unable to instantiate auth service object", e)
        } catch (e: NoSuchMethodException) {
            throw IllegalStateException("auth service object does not have a public constructor accepting a trace factory", e)
        } catch (e: InvocationTargetException) {
            throw IllegalStateException("error occurred during instantiation of auth service object", e)
        }
    }

    val authorizerGraph by Once { req(authorizerOgd).graph() }

    val authorizer by Once { req(authorizerGraph).authorizer() }

    val userHandler by Once { UserHandler(req(authorizer), req(baseCommGorgel).getChild(UserHandler::class)) }
            .wire {
                req(gatekeeper).refTable.addRef(it)
            }
            .eager()
}
