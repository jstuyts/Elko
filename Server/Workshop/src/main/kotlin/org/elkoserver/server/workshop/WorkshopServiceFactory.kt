package org.elkoserver.server.workshop

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory

/**
 * Service factory for the Workshop.
 *
 * The Workshop offers two kinds of service connections:
 *
 * workshop-service - for messages to objects offering services
 * workshop-admin - for system administrators
 */
internal class WorkshopServiceFactory(private val workshop: Workshop, private val tr: Trace, private val traceFactory: TraceFactory) : ServiceFactory {
    override fun provideFactory(label: String,
                                auth: AuthDesc,
                                allow: Set<String>,
                                serviceNames: MutableList<String>,
                                protocol: String): MessageHandlerFactory {
        var allowAdmin = false
        var allowClient = false
        if (allow.contains("any")) {
            allowAdmin = true
            allowClient = true
        }
        if (allow.contains("admin")) {
            allowAdmin = true
        }
        if (allow.contains("workshop")) {
            allowClient = true
        }
        if (allowAdmin) {
            serviceNames.add("workshop-admin")
        }
        if (allowClient) {
            serviceNames.add("workshop-service")
        }
        return WorkshopActorFactory(workshop, auth, allowAdmin, allowClient, tr, traceFactory)
    }
}
