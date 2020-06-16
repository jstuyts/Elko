@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.context

import org.elkoserver.foundation.json.ClockInjector
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.TraceFactoryInjector
import org.elkoserver.foundation.net.ConnectionRetrier
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.RunnerRef
import org.elkoserver.foundation.server.BaseConnectionSetup
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
import org.elkoserver.objdb.ObjDBLocal
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

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val contextorGorgel by Once { req(provided.baseGorgel()).getChild(Contextor::class) }

    val contextServiceFactoryGorgel by Once { req(provided.baseGorgel()).getChild(ContextServiceFactory::class) }

    val directorGroupGorgel by Once { req(provided.baseGorgel()).getChild(DirectorGroup::class) }

    val internalActorGorgel by Once { req(provided.baseGorgel()).getChild(InternalActor::class) }

    val jsonToObjectDeserializerGorgel by Once { req(provided.baseGorgel()).getChild(JsonToObjectDeserializer::class) }

    val objDbLocalGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBLocal::class) }

    val objDbRemoteGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBRemote::class) }

    val presencerGroupGorgel by Once { req(provided.baseGorgel()).getChild(PresencerGroup::class) }

    val reservationGorgel by Once { req(provided.baseGorgel()).getChild(Reservation::class) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val sessionClientGorgel by Once { req(provided.baseGorgel()).getChild(Session::class, Tag("category", "client")) }

    val staticObjectReceiverGorgel by Once { req(provided.baseGorgel()).getChild(StaticObjectList::class) }

    val userActorGorgel by Once { req(provided.baseGorgel()).getChild(UserActor::class) }

    val contextGorgelWithoutRef by Once { req(provided.baseGorgel()).getChild(Context::class) }

    val itemGorgelWithoutRef by Once { req(provided.baseGorgel()).getChild(Item::class) }

    val userGorgelWithoutRef by Once { req(provided.baseGorgel()).getChild(User::class) }

    val contextServiceFactory by Once {
        ContextServiceFactory(
                req(contextor),
                req(contextServiceFactoryGorgel),
                req(internalActorGorgel),
                req(userActorGorgel),
                req(userGorgelWithoutRef),
                req(provided.traceFactory()),
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

    val mustSendDebugReplies by Once { req(provided.props()).testProperty("conf.msgdiagnostics") }

    val server by Once {
        Server(
                req(provided.props()),
                "context",
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(serviceActorGorgel),
                req(baseConnectionSetupGorgel),
                req(objDbLocalGorgel),
                req(provided.baseGorgel()),
                req(connectionRetrierWithoutLabelGorgel),
                req(contTrace),
                req(provided.timer()),
                req(provided.clock()),
                req(provided.traceFactory()),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(serverTagGenerator),
                req(serverLoadMonitor),
                req(sessionIdGenerator),
                req(jsonToObjectDeserializer),
                req(runnerRef),
                req(objDBRemoteFactory),
                req(mustSendDebugReplies))
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

    val injectors by Once { listOf(req(clockInjector), req(traceFactoryInjector)) }

    val runnerRef by Once { RunnerRef(req(provided.traceFactory())) }
            .dispose { it.shutDown() }

    val serverTagGenerator by Once { LongIdGenerator() }

    val objDBRemoteFactory by Once {
        ObjDBRemoteFactory(
                req(provided.props()),
                req(objDbRemoteGorgel),
                req(connectionRetrierWithoutLabelGorgel),
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
                req(contTrace),
                req(contextorGorgel),
                req(contextGorgelWithoutRef),
                req(itemGorgelWithoutRef),
                req(staticObjectReceiverGorgel),
                req(directorGroupGorgel),
                req(presencerGroupGorgel),
                req(sessionClientGorgel),
                req(reservationGorgel),
                req(connectionRetrierWithoutLabelGorgel),
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
