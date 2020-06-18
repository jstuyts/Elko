@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.json.ClockInjector
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.RandomInjector
import org.elkoserver.foundation.json.TraceFactoryInjector
import org.elkoserver.foundation.net.ConnectionRetrier
import org.elkoserver.foundation.net.HTTPSessionConnection
import org.elkoserver.foundation.net.RTCPSessionConnection
import org.elkoserver.foundation.net.TCPConnection
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.RunnerRef
import org.elkoserver.foundation.server.BaseConnectionSetup
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
import org.elkoserver.objdb.ObjDBRemote
import org.elkoserver.objdb.ObjDBRemoteFactory
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
import org.ooverkommelig.ProvidedAdministration
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req
import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.time.Clock

internal class GatekeeperServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun traceFactory(): D<TraceFactory>
        fun timer(): D<Timer>
        fun clock(): D<Clock>
        fun props(): D<ElkoProperties>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
    }

    val gateTrace by Once { req(provided.traceFactory()).trace("gate") }

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class) }

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(GatekeeperBoot::class) }

    val brokerActorGorgel by Once { req(provided.baseGorgel()).getChild(BrokerActor::class, Tag("category", "comm")) }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val directorActorFactoryGorgel by Once { req(provided.baseGorgel()).getChild(DirectorActorFactory::class) }

    val directorActorGorgel by Once { req(provided.baseGorgel()).getChild(DirectorActor::class, Tag("category", "comm")) }

    val gatekeeperGorgel by Once { req(provided.baseGorgel()).getChild(Gatekeeper::class) }

    val gatekeeperActorGorgel by Once { req(provided.baseGorgel()).getChild(GatekeeperActor::class) }

    val jsonToObjectDeserializerGorgel by Once { req(provided.baseGorgel()).getChild(JsonToObjectDeserializer::class) }

    val objDbLocalGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBLocal::class) }

    val objDbRemoteGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBRemote::class) }

    val odbActorGorgel by Once { req(provided.baseGorgel()).getChild(ODBActor::class, Tag("category", "comm")) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val httpSessionConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(HTTPSessionConnection::class, Tag("category", "comm")) }
    val rtcpSessionConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(RTCPSessionConnection::class, Tag("category", "comm")) }
    val tcpConnectionCommGorgel by Once { req(provided.baseGorgel()).getChild(TCPConnection::class, Tag("category", "comm")) }
    val connectionBaseCommGorgel by Once { req(provided.baseGorgel()).withAdditionalStaticTags(Tag("category", "comm")) }

    val mustSendDebugReplies by Once { req(provided.props()).testProperty("conf.msgdiagnostics") }

    val server by Once {
        Server(
                req(provided.props()),
                "gatekeeper",
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(serviceActorGorgel),
                req(baseConnectionSetupGorgel),
                req(objDbLocalGorgel),
                req(provided.baseGorgel()),
                req(connectionRetrierWithoutLabelGorgel),
                req(brokerActorGorgel),
                req(httpSessionConnectionCommGorgel),
                req(rtcpSessionConnectionCommGorgel),
                req(tcpConnectionCommGorgel),
                req(connectionBaseCommGorgel),
                req(gateTrace),
                req(provided.timer()),
                req(provided.clock()),
                req(provided.traceFactory()),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(serverTagGenerator),
                req(serverLoadMonitor),
                req(sessionIdGenerator),
                req(connectionIdGenerator),
                req(jsonToObjectDeserializer),
                req(runnerRef),
                req(objDBRemoteFactory),
                req(mustSendDebugReplies))
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
                req(provided.traceFactory()),
                req(injectors))
    }

    val clockInjector by Once { ClockInjector(req(provided.clock())) }

    val traceFactoryInjector by Once { TraceFactoryInjector(req(provided.traceFactory())) }

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

    val injectors by Once { listOf(req(clockInjector), req(traceFactoryInjector), req(deserializedObjectRandomInjector), req(deserializedObjectMessageDigestInjector)) }

    val runnerRef by Once { RunnerRef(req(provided.traceFactory())) }
            .dispose { it.shutDown() }

    val serverTagGenerator by Once { LongIdGenerator() }

    val objDBRemoteFactory by Once {
        ObjDBRemoteFactory(
                req(provided.props()),
                req(objDbRemoteGorgel),
                req(connectionRetrierWithoutLabelGorgel),
                req(odbActorGorgel),
                req(provided.traceFactory()),
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

    val gatekeeper: D<Gatekeeper> by Once {
        Gatekeeper(
                req(server),
                req(gatekeeperGorgel),
                req(directorActorFactoryGorgel),
                req(connectionRetrierWithoutLabelGorgel),
                req(directorActorGorgel),
                req(gateTrace),
                req(provided.timer()),
                req(provided.traceFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(provided.props()),
                req(jsonToObjectDeserializer),
                req(mustSendDebugReplies))
    }

    val actionTimeout by Once { 1000 * req(provided.props()).intProperty("conf.gatekeeper.actiontimeout", GatekeeperBoot.DEFAULT_ACTION_TIMEOUT) }

    val gatekeeperServiceFactory by Once { GatekeeperServiceFactory(req(gatekeeper), req(actionTimeout), req(gatekeeperActorGorgel), req(provided.timer()), req(provided.traceFactory()), req(mustSendDebugReplies)) }

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
                override fun traceFactory() = provided.traceFactory()
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

    val userHandler by Once { UserHandler(req(authorizer), req(provided.traceFactory())) }
            .wire {
                req(gatekeeper).refTable.addRef(it)
            }
            .eager()
}
