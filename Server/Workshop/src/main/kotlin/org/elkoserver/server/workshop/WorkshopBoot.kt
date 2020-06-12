package org.elkoserver.server.workshop

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
 * The boot class for the Workshop.  The Workshop is a server that provides a
 * place for arbitrary, configurable worker objects to run.
 */
@Suppress("unused")
class WorkshopBoot : Bootable {
    override fun boot(props: ElkoProperties, gorgel: Gorgel, traceFactory: TraceFactory, clock: Clock) {
        val myGorgel = gorgel.getChild(WorkshopBoot::class)
        lateinit var workshopServerGraph: WorkshopServerOgd.Graph
        val graphClosingShutdownWatcher = object : ShutdownWatcher {
            override fun noteShutdown() {
                workshopServerGraph.close()
            }
        }
        workshopServerGraph = WorkshopServerOgd(object : WorkshopServerOgd.Provided, ProvidedAdministration() {
            override fun clock() = ConstantDefinition(clock)
            override fun baseGorgel() = ConstantDefinition(gorgel)
            override fun props() = ConstantDefinition(props)
            override fun traceFactory() = ConstantDefinition(traceFactory)
            override fun externalShutdownWatcher() = ConstantDefinition(graphClosingShutdownWatcher)
        }, ObjectGraphConfiguration(object : ObjectGraphLogger {
            override fun errorDuringCleanUp(sourceObject: Any, operation: String, exception: Exception) {
                myGorgel.error("Error during cleanup of object graph. Object: $sourceObject, operation: $operation", exception)
            }
        })).Graph()
        workshopServerGraph.server()
    }
}
