@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.context

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.LongIdGenerator
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.metadata.AuthDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
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
    }

    val contTrace by Once { req(provided.traceFactory()).trace("cont") }

    val contextorGorgel by Once { req(provided.baseGorgel()).getChild(Contextor::class) }

    val contextServiceFactoryGorgel by Once { req(provided.baseGorgel()).getChild(ContextServiceFactory::class) }

    val directorGroupGorgel by Once { req(provided.baseGorgel()).getChild(DirectorGroup::class) }

    val internalActorGorgel by Once { req(provided.baseGorgel()).getChild(InternalActor::class) }

    val presencerGroupGorgel by Once { req(provided.baseGorgel()).getChild(PresencerGroup::class) }

    val reservationGorgel by Once { req(provided.baseGorgel()).getChild(Reservation::class) }

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
                req(idGenerator))
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

    val server by Once {
        Server(
                req(provided.props()),
                "context",
                req(contTrace),
                req(provided.timer()),
                req(provided.clock()),
                req(provided.traceFactory()),
                req(provided.authDescFromPropertiesFactory()),
                req(provided.hostDescFromPropertiesFactory()),
                req(serverTagGenerator))
    }

    val serverTagGenerator by Once { LongIdGenerator() }

    val serverListeners by Once { req(server).listeners() }

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
                req(provided.timer()),
                req(provided.traceFactory()),
                req(provided.clock()),
                req(contextorEntryTimeout),
                req(contextorLimit),
                req(contextorRandom))
    }

    val contextorRandom by Once { SecureRandom() }

    val hostListScanner by Once { HostListScanner(req(provided.hostDescFromPropertiesFactory())) }

    val directors by Once { req(hostListScanner).scan("conf.register") }

    val presencers by Once { req(hostListScanner).scan("conf.presence") }

    companion object {
        private const val DEFAULT_ENTER_TIMEOUT_IN_SECONDS = 15
    }
}
