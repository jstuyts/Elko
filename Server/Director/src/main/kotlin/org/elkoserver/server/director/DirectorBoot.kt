package org.elkoserver.server.director

import org.elkoserver.foundation.boot.Bootable
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.time.Clock

/**
 * The Elko boot class for the Director.  The Director is a server that
 * provides load balancing and context location directory services for Elko
 * applications using the Context Server.
 */
@Suppress("unused")
class DirectorBoot : Bootable {
    private lateinit var traceFactory: TraceFactory
    private lateinit var tr: Trace
    private lateinit var myDirector: Director

    override fun boot(props: ElkoProperties, traceFactory: TraceFactory, clock: Clock) {
        this.traceFactory = traceFactory
        tr = traceFactory.trace("dire")
        val timer = Timer(traceFactory, clock)
        val server = Server(props, "director", tr, timer, clock, traceFactory)
        myDirector = Director(server, tr, traceFactory, clock)
        if (server.startListeners("conf.listen",
                        DirectorServiceFactory()) == 0) {
            tr.errori("no listeners specified")
        }
    }

    /**
     * Service factory for the Director.
     *
     * The Director offers three kinds of service connections:
     *
     * director/provider - for context servers offering services
     * director/user     - for clients seeking services
     * director/admin    - for system administrators
     */
    private inner class DirectorServiceFactory : ServiceFactory {
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
            return DirectorActorFactory(myDirector, auth, allowAdmin, allowProvider, allowUser, tr, traceFactory)
        }
    }
}