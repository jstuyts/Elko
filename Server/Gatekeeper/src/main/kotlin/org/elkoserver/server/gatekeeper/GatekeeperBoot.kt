package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.boot.Bootable
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.ShutdownWatcher
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
    override fun boot(props: ElkoProperties, gorgel: Gorgel, traceFactory: TraceFactory, clock: Clock) {
        val myGorgel = gorgel.getChild(GatekeeperBoot::class)
        lateinit var gatekeeperServerGraph: GatekeeperServerOgd.Graph
        val graphClosingShutdownWatcher = object : ShutdownWatcher {
            override fun noteShutdown() {
                gatekeeperServerGraph.close()
            }
        }
        gatekeeperServerGraph = GatekeeperServerOgd(object : GatekeeperServerOgd.Provided, ProvidedAdministration() {
            override fun clock() = ConstantDefinition(clock)
            override fun baseGorgel() = ConstantDefinition(gorgel)
            override fun traceFactory() = ConstantDefinition(traceFactory)
            override fun props() = ConstantDefinition(props)
            override fun externalShutdownWatcher() = ConstantDefinition(graphClosingShutdownWatcher)
        }, ObjectGraphConfiguration(object : ObjectGraphLogger {
            override fun errorDuringCleanUp(sourceObject: Any, operation: String, exception: Exception) {
                myGorgel.error("Error during cleanup of object graph. Object: $sourceObject, operation: $operation", exception)
            }
        })).Graph()
        gatekeeperServerGraph.server()
    }

    companion object {
        /** Default action timeout, in seconds.  */
        internal const val DEFAULT_ACTION_TIMEOUT = 15
    }
}
