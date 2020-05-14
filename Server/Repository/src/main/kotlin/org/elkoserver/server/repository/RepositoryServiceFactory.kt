package org.elkoserver.server.repository

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Service factor for the Repository.
 *
 * The Repository offers two kinds of service connections:
 *
 * repository/rep   - for object storage and retrieval
 * repository/admin - for system administrators
 */
internal class RepositoryServiceFactory(
        private val repository: Repository,
        private val repositoryActorGorgel: Gorgel,
        private val traceFactory: TraceFactory) : ServiceFactory {
    override fun provideFactory(label: String,
                                auth: AuthDesc,
                                allow: Set<String>,
                                serviceNames: MutableList<String>,
                                protocol: String): MessageHandlerFactory {
        var allowAdmin = false
        var allowRep = false
        if (allow.contains("any")) {
            allowAdmin = true
            allowRep = true
        }
        if (allow.contains("admin")) {
            allowAdmin = true
        }
        if (allow.contains("rep")) {
            allowRep = true
        }
        if (allowAdmin) {
            serviceNames.add("repository-admin")
        }
        if (allowRep) {
            serviceNames.add("repository-rep")
        }
        return RepositoryActorFactory(repository, auth, allowAdmin,
                allowRep, repositoryActorGorgel, traceFactory)
    }
}
