package org.elkoserver.server.workshop

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
 * The boot class for the Workshop.  The Workshop is a server that provides a
 * place for arbitrary, configurable worker objects to run.
 */
@Suppress("unused")
class WorkshopBoot : Bootable {
    private lateinit var tr: Trace
    private lateinit var myWorkshop: Workshop
    private lateinit var traceFactory: TraceFactory

    override fun boot(props: ElkoProperties, gorgel: Gorgel, traceFactory: TraceFactory, clock: Clock) {
        val myGorgel = gorgel.getChild(WorkshopBoot::class)
        this.traceFactory = traceFactory
        tr = traceFactory.trace("work")
        val workshopServerGraph = WorkshopServerOgd(object : WorkshopServerOgd.Provided, ProvidedAdministration() {
            override fun clock() = ConstantDefinition(clock)
            override fun gorgel() = ConstantDefinition(gorgel)
        }, ObjectGraphConfiguration(object : ObjectGraphLogger {
            override fun errorDuringCleanUp(sourceObject: Any, operation: String, exception: Exception) {
                myGorgel.error("Error during cleanup of object graph. Object: $sourceObject, operation: $operation", exception)
            }
        })).Graph()
        val timer = workshopServerGraph.timer()
        val server = Server(props, "workshop", tr, timer, clock, traceFactory)
        myWorkshop = Workshop(server, tr, traceFactory, clock)
        if (server.startListeners("conf.listen",
                        WorkshopServiceFactory()) == 0) {
            tr.errori("no listeners specified")
        } else {
            myWorkshop.loadStartupWorkers()
        }
    }

    /**
     * Service factory for the Workshop.
     *
     * The Workshop offers two kinds of service connections:
     *
     * workshop-service - for messages to objects offering services
     * workshop-admin - for system administrators
     */
    private inner class WorkshopServiceFactory : ServiceFactory {
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
            return WorkshopActorFactory(myWorkshop, auth, allowAdmin,
                    allowClient, tr, traceFactory)
        }
    }
}
