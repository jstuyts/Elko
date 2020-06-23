@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.repository

import org.elkoserver.foundation.json.ClockInjector
import org.elkoserver.foundation.json.ConstructorInvoker
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MethodInvoker
import org.elkoserver.foundation.json.TraceFactoryInjector
import org.elkoserver.foundation.net.ChunkyByteArrayInputStream
import org.elkoserver.foundation.net.ConnectionRetrier
import org.elkoserver.foundation.net.HTTPSessionConnection
import org.elkoserver.foundation.net.JSONByteIOFramer
import org.elkoserver.foundation.net.JSONHTTPFramer
import org.elkoserver.foundation.net.Listener
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.net.RTCPSessionConnection
import org.elkoserver.foundation.net.SelectThread
import org.elkoserver.foundation.net.SslSetup
import org.elkoserver.foundation.net.TCPConnection
import org.elkoserver.foundation.net.WebSocketByteIOFramerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.foundation.server.BaseConnectionSetup
import org.elkoserver.foundation.server.BrokerActor
import org.elkoserver.foundation.server.LoadWatcher
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServerLoadMonitor
import org.elkoserver.foundation.server.ServerLoadMonitor.Companion.DEFAULT_LOAD_SAMPLE_TIMEOUT
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
import org.elkoserver.objdb.ObjectStoreFactory
import org.elkoserver.objdb.PutRequestFactory
import org.elkoserver.objdb.QueryRequestFactory
import org.elkoserver.objdb.RemoveRequestFactory
import org.elkoserver.objdb.UpdateRequestFactory
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

internal class RepositoryServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun traceFactory(): D<TraceFactory>
        fun props(): D<ElkoProperties>
        fun timer(): D<Timer>
        fun clock(): D<Clock>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
    }

    val repoTrace by Once { req(provided.traceFactory()).trace("repo") }

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class) }

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(RepositoryBoot::class) }

    val brokerActorGorgel by Once { req(provided.baseGorgel()).getChild(BrokerActor::class, Tag("category", "comm")) }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

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

    val repositoryActorGorgel by Once { req(provided.baseGorgel()).getChild(RepositoryActor::class) }

    val repositoryActorCommGorgel by Once { req(repositoryActorGorgel).withAdditionalStaticTags(Tag("category", "comm")) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceActorCommGorgel by Once { req(serviceActorGorgel).withAdditionalStaticTags(Tag("category", "comm")) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val httpSessionConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(HTTPSessionConnection::class, Tag("category", "comm")) }
    val rtcpSessionConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(RTCPSessionConnection::class, Tag("category", "comm")) }
    val tcpConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(TCPConnection::class, Tag("category", "comm")) }
    val connectionBaseCommGorgel by Once { req(provided.baseGorgel()).withAdditionalStaticTags(Tag("category", "comm")) }

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

    val networkManager by Once {
        NetworkManager(
                req(provided.props()),
                req(serverLoadMonitor),
                req(runner),
                req(provided.timer()),
                req(provided.clock()),
                req(httpSessionConnectionCommGorgel),
                req(rtcpSessionConnectionCommGorgel),
                req(connectionBaseCommGorgel),
                req(provided.traceFactory()),
                req(inputGorgel),
                req(sessionIdGenerator),
                req(connectionIdGenerator),
                req(mustSendDebugReplies),
                req(selectThread))
    }

    val objDBLocalFactory by Once {
        ObjDBLocalFactory(
                req(provided.props()),
                req(objDbLocalGorgel),
                req(provided.baseGorgel()),
                req(provided.traceFactory()),
                req(jsonToObjectDeserializer),
                req(runner))
    }

    val server by Once {
        Server(
                req(provided.props()),
                "rep",
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(serviceActorGorgel),
                req(serviceActorCommGorgel),
                req(baseConnectionSetupGorgel),
                req(listenerGorgel),
                req(jsonHttpFramerCommGorgel),
                req(tcpConnectionGorgel),
                req(connectionRetrierWithoutLabelGorgel),
                req(jsonByteIoFramerWithoutLabelGorgel),
                req(websocketFramerGorgel),
                req(brokerActorGorgel),
                req(repoTrace),
                req(provided.timer()),
                req(provided.traceFactory()),
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
                req(networkManager),
                req(objDBLocalFactory))
    }
            .wire {
                it.registerShutdownWatcher(req(provided.externalShutdownWatcher()))
            }
            .init {
                if (it.startListeners("conf.listen", req(repositoryServiceFactory)) == 0) {
                    req(bootGorgel).error("no listeners specified")
                }
            }

    val serverLoadMonitor by Once {
        ServerLoadMonitor(
                req(provided.timer()),
                req(provided.clock()),
                req(provided.props()).intProperty("conf.load.time", DEFAULT_LOAD_SAMPLE_TIMEOUT) * 1000)
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

    val runner by Once { Runner(req(provided.traceFactory())) }
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
                req(mustSendDebugReplies))
    }

    val getRequestFactory by Once { GetRequestFactory(req(requestTagGenerator)) }

    val putRequestFactory by Once { PutRequestFactory(req(requestTagGenerator)) }

    val updateRequestFactory by Once { UpdateRequestFactory(req(requestTagGenerator)) }

    val queryRequestFactory by Once { QueryRequestFactory(req(requestTagGenerator)) }

    val removeRequestFactory by Once { RemoveRequestFactory(req(requestTagGenerator)) }

    val requestTagGenerator by Once { LongIdGenerator(1L) }

    val repository: D<Repository> by Once { Repository(req(server), req(methodInvokerCommGorgel), req(provided.traceFactory()), req(objectStore), req(jsonToObjectDeserializer)) }

    val objectStore by Once { ObjectStoreFactory.createAndInitializeObjectStore(req(provided.props()), "conf.rep", req(provided.baseGorgel())) }

    val repositoryServiceFactory by Once {
        RepositoryServiceFactory(
                req(repository),
                req(repositoryActorGorgel),
                req(repositoryActorCommGorgel),
                req(mustSendDebugReplies))
    }
}
