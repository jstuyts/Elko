@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.repository

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
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

internal class RepositoryServerSgd(provded: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provded, configuration) {
    interface Provided : ProvidedBase {
        fun traceFactory(): D<TraceFactory>
        fun props(): D<ElkoProperties>
        fun timer(): D<Timer>
        fun clock(): D<Clock>
        fun baseGorgel(): D<Gorgel>
    }

    val repoTrace by Once { req(provded.traceFactory()).trace("repo") }

    val bootGorgel by Once { req(provded.baseGorgel()).getChild(RepositoryBoot::class) }

    val server by Once { Server(req(provded.props()), "rep", req(repoTrace), req(provded.timer()), req(provded.clock()), req(provded.traceFactory())) }
            .init {
                if (it.startListeners("conf.listen", req(repositoryServiceFactory)) == 0) {
                    req(bootGorgel).error("no listeners specified")
                }
            }

    val repository: D<Repository> by Once { Repository(req(server), req(repoTrace), req(provded.traceFactory()), req(provded.clock())) }

    val repositoryServiceFactory by Once { RepositoryServiceFactory(req(repository), req(repoTrace), req(provded.traceFactory())) }
}
