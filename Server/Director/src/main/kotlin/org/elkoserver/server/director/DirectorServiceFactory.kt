package org.elkoserver.server.director

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Service factory for the Director.
 *
 * The Director offers three kinds of service connections:
 *
 * director/provider - for context servers offering services
 * director/user     - for clients seeking services
 * director/admin    - for system administrators
 */
internal class DirectorServiceFactory(
        private val director: Director,
        private val directorActorGorgel: Gorgel,
        private val providerGorgel: Gorgel,
        private val adminFactory: AdminFactory,
        private val providerFactory: ProviderFactory,
        private val mustSendDebugReplies: Boolean) : ServiceFactory {
    override fun provideFactory(label: String,
                                auth: AuthDesc,
                                allow: Set<String>,
                                serviceNames: MutableList<String>,
                                protocol: String): MessageHandlerFactory {
        var allowAdmin = false
        var allowProvider = false
        var allowUser = false
        if (allow.contains("any")) {
            allowAdmin = true
            allowProvider = true
            allowUser = true
        }
        if (allow.contains("admin")) {
            allowAdmin = true
        }
        if (allow.contains("provider")) {
            allowProvider = true
        }
        if (allow.contains("user")) {
            allowUser = true
        }
        if (allowAdmin) {
            serviceNames.add("director-admin")
        }
        if (allowProvider) {
            serviceNames.add("director-provider")
        }
        if (allowUser) {
            serviceNames.add("director-user")
        }
        return DirectorActorFactory(director, auth, allowAdmin, allowProvider, allowUser, directorActorGorgel, providerGorgel, adminFactory, providerFactory, mustSendDebugReplies)
    }
}