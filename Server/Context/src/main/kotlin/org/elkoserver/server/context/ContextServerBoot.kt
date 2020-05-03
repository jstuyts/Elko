package org.elkoserver.server.context

import org.elkoserver.foundation.boot.Bootable
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.ConstantDefinition
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphLogger
import org.ooverkommelig.ProvidedAdministration
import java.time.Clock

/**
 * The Elko boot class for the Context Server, the basis of Elko applications
 * built on the context/user/item object model.
 */
@Suppress("unused")
class ContextServerBoot : Bootable {
    override fun boot(props: ElkoProperties, gorgel: Gorgel, traceFactory: TraceFactory, clock: Clock) {
        val myGorgel = gorgel.getChild(ContextServerBoot::class)
        val contextServerGraph = ContextServerOgd(object : ContextServerOgd.Provided, ProvidedAdministration() {
            override fun clock() = ConstantDefinition(clock)
            override fun props() = ConstantDefinition(props)
            override fun rootGorgel() = ConstantDefinition(gorgel)
            override fun traceFactory() = ConstantDefinition(traceFactory)
        }, ObjectGraphConfiguration(object : ObjectGraphLogger {
            override fun errorDuringCleanUp(sourceObject: Any, operation: String, exception: Exception) {
                myGorgel.error("Error during cleanup of object graph. Object: $sourceObject, operation: $operation", exception)
            }
        })).Graph()
        contextServerGraph.contextor()
    }
}
