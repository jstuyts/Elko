package org.elkoserver.server.presence

import org.elkoserver.foundation.boot.Bootable
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.ConstantDefinition
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphLogger
import java.time.Clock

/**
 * The Elko boot class for the Presence Server.  This server allows a group of
 * Context Servers to keep track of the online presences of the various other
 * users in their own users' social graphs.
 */
@Suppress("unused")
class PresenceServerBoot : Bootable {
    override fun boot(props: ElkoProperties, gorgel: Gorgel, clock: Clock) {
        val myGorgel = gorgel.getChild(PresenceServerBoot::class)
        lateinit var presenceServerGraph: PresenceServerOgd.Graph
        val graphClosingShutdownWatcher = object : ShutdownWatcher {
            override fun noteShutdown() {
                presenceServerGraph.close()
            }
        }
        presenceServerGraph = PresenceServerOgd(object : PresenceServerOgd.Provided {
            override fun clock() = ConstantDefinition(clock)
            override fun baseGorgel() = ConstantDefinition(gorgel)
            override fun props() = ConstantDefinition(props)
            override fun externalShutdownWatcher() = ConstantDefinition(graphClosingShutdownWatcher)
        }, ObjectGraphConfiguration(object : ObjectGraphLogger {
            override fun errorDuringCleanUp(sourceObject: Any, operation: String, exception: Exception) {
                myGorgel.error("Error during cleanup of object graph. Object: $sourceObject, operation: $operation", exception)
            }
        })).Graph()
        presenceServerGraph.server()
    }
}
