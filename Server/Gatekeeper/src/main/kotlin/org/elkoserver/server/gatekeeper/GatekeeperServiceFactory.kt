package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Service factory for the Gatekeeper.
 *
 * The Gatekeeper offers two kinds of service connections:
 *
 * gatekeeper/user     - for clients seeking services
 * gatekeeper/admin    - for system administrators
 */
internal class GatekeeperServiceFactory(
        private val gatekeeper: Gatekeeper,
        private val actionTimeout: Int,
        private val gatekeeperActorGorgel: Gorgel,
        private val timer: Timer,
        private val traceFactory: TraceFactory) : ServiceFactory {
    override fun provideFactory(label: String,
                                auth: AuthDesc,
                                allow: Set<String>,
                                serviceNames: MutableList<String>,
                                protocol: String): MessageHandlerFactory {
        var allowUser = false
        var allowAdmin = false
        if (allow.contains("any")) {
            allowUser = true
            allowAdmin = true
        }
        if (allow.contains("admin")) {
            allowAdmin = true
        }
        if (allow.contains("user")) {
            allowUser = true
        }
        if (allowAdmin) {
            serviceNames.add("gatekeeper-admin")
        }
        if (allowUser) {
            serviceNames.add("gatekeeper-user")
        }
        return GatekeeperActorFactory(gatekeeper, auth, allowAdmin,
                allowUser, actionTimeout, gatekeeperActorGorgel, timer, traceFactory)
    }
}
