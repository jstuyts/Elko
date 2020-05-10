package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.boot.Bootable
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.ConstantDefinition
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphLogger
import org.ooverkommelig.ProvidedAdministration
import java.time.Clock

/**
 * The Elko boot class for the Gatekeeper.  The Gatekeeper is a server that
 * provides login reservation and authentication services for other Elko
 * servers such as the Director.
 */
@Suppress("unused")
class GatekeeperBoot : Bootable {
    private lateinit var traceFactory: TraceFactory
    private lateinit var tr: Trace
    private lateinit var timer: Timer
    private lateinit var myGatekeeper: Gatekeeper

    /** How long user has before being kicked off, in milliseconds.  */
    private var myActionTimeout = 0

    override fun boot(props: ElkoProperties, gorgel: Gorgel, traceFactory: TraceFactory, clock: Clock) {
        val myGorgel = gorgel.getChild(GatekeeperBoot::class)
        this.traceFactory = traceFactory
        tr = traceFactory.trace("gate")
        val gatekeeperServerGraph = GatekeeperServerOgd(object : GatekeeperServerOgd.Provided, ProvidedAdministration() {
            override fun clock() = ConstantDefinition(clock)
            override fun gorgel() = ConstantDefinition(gorgel)
        }, ObjectGraphConfiguration(object : ObjectGraphLogger {
            override fun errorDuringCleanUp(sourceObject: Any, operation: String, exception: Exception) {
                myGorgel.error("Error during cleanup of object graph. Object: $sourceObject, operation: $operation", exception)
            }
        })).Graph()
        val timer = gatekeeperServerGraph.timer()
        val server = Server(props, "gatekeeper", tr, timer, clock, traceFactory)
        myGatekeeper = Gatekeeper(server, tr, timer, traceFactory, clock)
        myActionTimeout = 1000 * props.intProperty("conf.gatekeeper.actiontimeout", DEFAULT_ACTION_TIMEOUT)
        if (server.startListeners("conf.listen", GatekeeperServiceFactory()) == 0) {
            tr.errori("no listeners specified")
        }
    }

    /**
     * Service factory for the Gatekeeper.
     *
     * The Gatekeeper offers two kinds of service connections:
     *
     * gatekeeper/user     - for clients seeking services
     * gatekeeper/admin    - for system administrators
     */
    private inner class GatekeeperServiceFactory : ServiceFactory {
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
            return GatekeeperActorFactory(myGatekeeper, auth, allowAdmin,
                    allowUser, myActionTimeout, tr, timer, traceFactory)
        }
    }

    companion object {
        /** Default action timeout, in seconds.  */
        private const val DEFAULT_ACTION_TIMEOUT = 15
    }
}