package org.elkoserver.server.context

import org.elkoserver.foundation.boot.Bootable
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.ConstantDefinition
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphLogger
import java.time.Clock

/**
 * The Elko boot class for the Context Server, the basis of Elko applications
 * built on the context/user/item object model.
 */
@Suppress("unused")
class ContextServerBoot : Bootable {
    override fun boot(props: ElkoProperties, gorgel: Gorgel, clock: Clock) {
        val myGorgel = gorgel.getChild(ContextServerBoot::class)
        lateinit var contextServerGraph: ContextServerOgd.Graph
        val graphClosingShutdownWatcher = ShutdownWatcher { contextServerGraph.close() }
        contextServerGraph = ContextServerOgd(object : ContextServerOgd.Provided {
            override fun clock() = ConstantDefinition(clock)
            override fun props() = ConstantDefinition(props)
            override fun baseGorgel() = ConstantDefinition(gorgel)
            override fun externalShutdownWatcher() = ConstantDefinition(graphClosingShutdownWatcher)
        }, ObjectGraphConfiguration(object : ObjectGraphLogger {
            override fun errorDuringCleanUp(sourceObject: Any, operation: String, exception: Exception) {
                myGorgel.error(
                    "Error during cleanup of object graph. Object: $sourceObject, operation: $operation",
                    exception
                )
            }
        })).Graph()
        contextServerGraph.contextor()
    }
}
