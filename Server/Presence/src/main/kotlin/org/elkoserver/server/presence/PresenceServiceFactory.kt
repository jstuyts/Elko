package org.elkoserver.server.presence

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Service factory for the Presence Server.
 *
 * The Presence Server offers two kinds of service connections:
 *
 * presence/client - for context servers monitoring presence information
 * presence/admin  - for system administrators
 */
internal class PresenceServiceFactory(
        private val presenceServer: PresenceServer,
        private val presenceActorGorgel: Gorgel,
        private val traceFactory: TraceFactory) : ServiceFactory {
    override fun provideFactory(label: String,
                                auth: AuthDesc,
                                allow: Set<String>,
                                serviceNames: MutableList<String>,
                                protocol: String): MessageHandlerFactory {
        var allowClient = false
        var allowAdmin = false
        if (allow.contains("any")) {
            allowClient = true
            allowAdmin = true
        }
        if (allow.contains("admin")) {
            allowAdmin = true
        }
        if (allow.contains("client")) {
            allowClient = true
        }
        if (allowAdmin) {
            serviceNames.add("presence-admin")
        }
        if (allowClient) {
            serviceNames.add("presence-client")
        }
        return PresenceActorFactory(presenceServer, auth, allowAdmin, allowClient, presenceActorGorgel, traceFactory)
    }
}
