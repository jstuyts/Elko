package org.elkoserver.server.repository

import org.elkoserver.foundation.boot.Bootable
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.ConstantDefinition
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphLogger
import org.ooverkommelig.ProvidedAdministration
import java.time.Clock

/**
 * The Elko boot class for the Repository.  The Repository is a server that
 * provides access to persistent storage for objects.
 */
@Suppress("unused")
class RepositoryBoot : Bootable {
    private lateinit var traceFactory: TraceFactory
    private lateinit var tr: Trace
    private lateinit var myRepository: Repository

    override fun boot(props: ElkoProperties, gorgel: Gorgel, traceFactory: TraceFactory, clock: Clock) {
        val myGorgel = gorgel.getChild(RepositoryBoot::class)
        this.traceFactory = traceFactory
        tr = traceFactory.trace("repo")
        val repositoryServerGraph = RepositoryServerOgd(object : RepositoryServerOgd.Provided, ProvidedAdministration() {
            override fun clock() = ConstantDefinition(clock)
            override fun gorgel() = ConstantDefinition(gorgel)
        }, ObjectGraphConfiguration(object : ObjectGraphLogger {
            override fun errorDuringCleanUp(sourceObject: Any, operation: String, exception: Exception) {
                myGorgel.error("Error during cleanup of object graph. Object: $sourceObject, operation: $operation", exception)
            }
        })).Graph()
        val timer = repositoryServerGraph.timer()
        val server = Server(props, "rep", tr, timer, clock, traceFactory)
        myRepository = Repository(server, tr, traceFactory, clock)
        if (server.startListeners("conf.listen",
                        RepositoryServiceFactory()) == 0) {
            tr.errori("no listeners specified")
        }
    }

    /**
     * Service factor for the Repository.
     *
     * The Repository offers two kinds of service connections:
     *
     * repository/rep   - for object storage and retrieval
     * repository/admin - for system administrators
     */
    private inner class RepositoryServiceFactory : ServiceFactory {
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
            return RepositoryActorFactory(myRepository, auth, allowAdmin,
                    allowRep, tr, traceFactory)
        }
    }
}
