@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.net.ConnectionRetrier
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.BaseConnectionSetup
import org.elkoserver.foundation.server.LoadWatcher
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServerLoadMonitor
import org.elkoserver.foundation.server.ServiceActor
import org.elkoserver.foundation.server.ServiceLink
import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.LongIdGenerator
import org.elkoserver.idgeneration.RandomIdGenerator
import org.elkoserver.objdb.ObjDBLocal
import org.elkoserver.objdb.ObjDBRemote
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedAdministration
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req
import java.lang.reflect.InvocationTargetException
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
    }

    val gateTrace by Once { req(provided.traceFactory()).trace("gate") }

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class)}

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(GatekeeperBoot::class) }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val directorActorFactoryGorgel by Once { req(provided.baseGorgel()).getChild(DirectorActorFactory::class) }

    val gatekeeperGorgel by Once { req(provided.baseGorgel()).getChild(Gatekeeper::class) }

    val gatekeeperActorGorgel by Once { req(provided.baseGorgel()).getChild(GatekeeperActor::class) }

    val jsonToObjectDeserializerGorgel by Once { req(provided.baseGorgel()).getChild(JsonToObjectDeserializer::class) }

    val objDbLocalGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBLocal::class) }

    val objDbRemoteGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBRemote::class) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val server by Once {
        Server(
                req(provided.props()),
                "gatekeeper",
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(serviceActorGorgel),
                req(baseConnectionSetupGorgel),
                req(objDbLocalGorgel),
                req(objDbRemoteGorgel),
                req(provided.baseGorgel()),
                req(connectionRetrierWithoutLabelGorgel),
                req(gateTrace),
                req(provided.timer()),
                req(provided.clock()),
                req(provided.traceFactory()),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(serverTagGenerator),
                req(serverLoadMonitor),
                req(sessionIdGenerator),
                req(jsonToObjectDeserializer))
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

    val sessionIdRandom by Once { SecureRandom() }
            .init { it.nextBoolean() }

    val jsonToObjectDeserializer by Once { JsonToObjectDeserializer(req(jsonToObjectDeserializerGorgel), req(provided.traceFactory()), req(provided.clock())) }

    val serverTagGenerator by Once { LongIdGenerator() }

    val gatekeeper: D<Gatekeeper> by Once {
        Gatekeeper(
                req(server),
                req(gatekeeperGorgel),
                req(directorActorFactoryGorgel),
                req(connectionRetrierWithoutLabelGorgel),
                req(gateTrace),
                req(provided.timer()),
                req(provided.traceFactory()),
                req(provided.clock()),
                req(provided.hostDescFromPropertiesFactory()),
                req(provided.props()),
                req(jsonToObjectDeserializer))
    }

    val actionTimeout by Once { 1000 * req(provided.props()).intProperty("conf.gatekeeper.actiontimeout", GatekeeperBoot.DEFAULT_ACTION_TIMEOUT) }

    val gatekeeperServiceFactory by Once { GatekeeperServiceFactory(req(gatekeeper), req(actionTimeout), req(gatekeeperActorGorgel), req(provided.timer()), req(provided.traceFactory())) }

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
