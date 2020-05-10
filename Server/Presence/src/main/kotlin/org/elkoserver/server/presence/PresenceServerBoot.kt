package org.elkoserver.server.presence

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
 * The Elko boot class for the Presence Server.  This server allows a group of
 * Context Servers to keep track of the online presences of the various other
 * users in their own users' social graphs.
 */
@Suppress("unused")
class PresenceServerBoot : Bootable {
    private lateinit var traceFactory: TraceFactory
    private lateinit var tr: Trace
    private lateinit var myPresenceServer: PresenceServer

    override fun boot(props: ElkoProperties, gorgel: Gorgel, traceFactory: TraceFactory, clock: Clock) {
        val myGorgel = gorgel.getChild(PresenceServerBoot::class)
        this.traceFactory = traceFactory
        tr = traceFactory.trace("pres")
        val presenceServerGraph = PresenceServerOgd(object : PresenceServerOgd.Provided, ProvidedAdministration() {
            override fun clock() = ConstantDefinition(clock)
            override fun gorgel() = ConstantDefinition(gorgel)
        }, ObjectGraphConfiguration(object : ObjectGraphLogger {
            override fun errorDuringCleanUp(sourceObject: Any, operation: String, exception: Exception) {
                myGorgel.error("Error during cleanup of object graph. Object: $sourceObject, operation: $operation", exception)
            }
        })).Graph()
        val timer = presenceServerGraph.timer()
        val server = Server(props, "presence", tr, timer, clock, traceFactory)
        myPresenceServer = PresenceServer(server, tr, traceFactory, clock)
        if (server.startListeners("conf.listen",
                        PresenceServiceFactory()) == 0) {
            tr.errori("no listeners specified")
        }
    }

    /**
     * Service factory for the Presence Server.
     *
     * The Presence Server offers two kinds of service connections:
     *
     * presence/client - for context servers monitoring presence information
     * presence/admin  - for system administrators
     */
    private inner class PresenceServiceFactory : ServiceFactory {
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
            return PresenceActorFactory(myPresenceServer, auth, allowAdmin,
                    allowClient, tr, traceFactory)
        }
    }
}
