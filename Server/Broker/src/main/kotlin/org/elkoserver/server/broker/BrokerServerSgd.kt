@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.broker

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
import org.elkoserver.ordinalgeneration.LongOrdinalGenerator
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req
import java.security.SecureRandom
import java.time.Clock

internal class BrokerServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun traceFactory(): D<TraceFactory>
        fun timer(): D<Timer>
        fun props(): D<ElkoProperties>
        fun clock(): D<Clock>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
        fun externalShutdownWatcher(): D<ShutdownWatcher>
    }

    val brokTrace by Once { req(provided.traceFactory()).trace("brok") }

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class) }

    val brokerBootGorgel by Once { req(provided.baseGorgel()).getChild(BrokerBoot::class) }

    val brokerGorgel by Once { req(provided.baseGorgel()).getChild(Broker::class) }

    val brokerActorGorgel by Once { req(provided.baseGorgel()).getChild(BrokerActor::class) }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val jsonToObjectDeserializerGorgel by Once { req(provided.baseGorgel()).getChild(JsonToObjectDeserializer::class) }

    val launcherTableGorgel by Once { req(provided.baseGorgel()).getChild(LauncherTable::class) }

    val objDbLocalGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBLocal::class) }

    val objDbRemoteGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBRemote::class) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val server by Once {
        Server(
                req(provided.props()),
                "broker",
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(serviceActorGorgel),
                req(baseConnectionSetupGorgel),
                req(objDbLocalGorgel),
                req(provided.baseGorgel()),
                req(connectionRetrierWithoutLabelGorgel),
                req(brokTrace),
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

    val mustSendDebugReplies by Once { req(provided.props()).testProperty("conf.msgdiagnostics") }

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

    val broker: D<Broker> by Once {
        Broker(
                req(server),
                req(brokerGorgel),
                req(launcherTableGorgel),
                req(provided.timer()),
                req(provided.traceFactory()),
                req(startMode),
                req(jsonToObjectDeserializer))
    }

    val brokerServiceFactory by Once {
        BrokerServiceFactory(
                req(broker),
                req(brokerActorGorgel),
                req(provided.traceFactory()),
                req(clientOrdinalGenerator),
                req(mustSendDebugReplies))
    }

    val clientOrdinalGenerator by Once { LongOrdinalGenerator() }
}
