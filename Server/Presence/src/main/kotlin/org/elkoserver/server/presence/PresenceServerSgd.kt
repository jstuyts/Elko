@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.presence

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req
import java.time.Clock

internal class PresenceServerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun traceFactory(): D<TraceFactory>
        fun timer(): D<Timer>
        fun clock(): D<Clock>
        fun props(): D<ElkoProperties>
        fun baseGorgel(): D<Gorgel>
        fun authDescFromPropertiesFactory(): D<AuthDescFromPropertiesFactory>
        fun hostDescFromPropertiesFactory(): D<HostDescFromPropertiesFactory>
    }

    val presTrace by Once { req(provided.traceFactory()).trace("pres") }

    val bootGorgel by Once { req(provided.baseGorgel()).getChild(PresenceServerBoot::class) }

    val graphDescGorgel by Once { req(provided.baseGorgel()).getChild(GraphDesc::class) }

    val presenceActorGorgel by Once { req(provided.baseGorgel()).getChild(PresenceActor::class) }

    val presenceServerGorgel by Once { req(provided.baseGorgel()).getChild(PresenceServer::class) }

    val socialGraphGorgel by Once { req(provided.baseGorgel()).getChild(SocialGraph::class) }

    val server by Once {
        Server(
                req(provided.props()),
                "presence",
                req(presTrace),
                req(provided.timer()),
                req(provided.clock()),
                req(provided.traceFactory()),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()))
    }
            .init {
                if (it.startListeners("conf.listen", req(presenceServiceFactory)) == 0) {
                    req(bootGorgel).error("no listeners specified")
                }
            }

    val presenceServer: D<PresenceServer> by Once { PresenceServer(req(server), req(presenceServerGorgel), req(graphDescGorgel), req(socialGraphGorgel), req(provided.traceFactory()), req(provided.clock())) }

    val presenceServiceFactory by Once { PresenceServiceFactory(req(presenceServer), req(presenceActorGorgel), req(provided.traceFactory())) }
}
