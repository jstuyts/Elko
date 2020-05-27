@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.director

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
import org.elkoserver.server.director.Director.Companion.DEFAULT_ESTIMATED_LOAD_INCREMENT
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

internal class DirectorServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun traceFactory(): D<TraceFactory>
        fun timer(): D<Timer>
        fun clock(): D<Clock>
        fun props(): D<ElkoProperties>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
    }

    val direTrace by Once { req(provided.traceFactory()).trace("dire") }

    val baseConnectionSetupGorgel by Once { req(provided.baseGorgel()).getChild(BaseConnectionSetup::class)}

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(DirectorBoot::class) }

    val connectionRetrierWithoutLabelGorgel by Once { req(provided.baseGorgel()).getChild(ConnectionRetrier::class) }

    val directorGorgel by Once { req(provided.baseGorgel()).getChild(Director::class) }

    val directorActorGorgel by Once { req(provided.baseGorgel()).getChild(DirectorActor::class) }

    val objDbLocalGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBLocal::class) }

    val objDbRemoteGorgel by Once { req(provided.baseGorgel()).getChild(ObjDBRemote::class) }

    val providerGorgel by Once { req(provided.baseGorgel()).getChild(Provider::class) }

    val serverGorgel by Once { req(provided.baseGorgel()).getChild(Server::class) }

    val serverLoadMonitorGorgel by Once { req(provided.baseGorgel()).getChild(ServerLoadMonitor::class) }

    val serviceActorGorgel by Once { req(provided.baseGorgel()).getChild(ServiceActor::class) }

    val serviceLinkGorgel by Once { req(provided.baseGorgel()).getChild(ServiceLink::class) }

    val server by Once {
        Server(
                req(provided.props()),
                "director",
                req(serverGorgel),
                req(serviceLinkGorgel),
                req(serviceActorGorgel),
                req(baseConnectionSetupGorgel),
                req(objDbLocalGorgel),
                req(objDbRemoteGorgel),
                req(provided.baseGorgel()),
                req(connectionRetrierWithoutLabelGorgel),
                req(direTrace),
                req(provided.timer()),
                req(provided.clock()),
                req(provided.traceFactory()),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(serverTagGenerator),
                req(serverLoadMonitor),
                req(sessionIdGenerator))
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

    val sessionIdRandom by Once { SecureRandom() }
            .init { it.nextBoolean() }

    val serverTagGenerator by Once { LongIdGenerator() }

    val providerLimit by Once { req(provided.props()).intProperty("conf.director.providerlimit", 0) }

    val estimatedLoadIncrement by Once { req(provided.props()).doubleProperty("conf.director.estloadbump", DEFAULT_ESTIMATED_LOAD_INCREMENT) }

    val director: D<Director> by Once {
        Director(
                req(server),
                req(directorGorgel),
                req(provided.traceFactory()),
                req(provided.clock()),
                req(random),
                req(estimatedLoadIncrement),
                req(providerLimit))
    }

    val random by Once { SecureRandom() }

    val directorServiceFactory by Once { DirectorServiceFactory(req(director), req(directorActorGorgel), req(providerGorgel), req(provided.traceFactory()), req(ordinalGenerator)) }

    val ordinalGenerator by Once { LongOrdinalGenerator() }
}
