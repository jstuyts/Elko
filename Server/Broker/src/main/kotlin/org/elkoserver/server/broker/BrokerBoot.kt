package org.elkoserver.server.broker

import org.elkoserver.foundation.boot.Bootable
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.ConstantDefinition
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphLogger
import java.time.Clock

/**
 * The Elko boot class for the Broker.  The Broker is a server allows a
 * cluster of Elko servers of various kinds to find out information about each
 * other's available services (and thus establish interconnectivity) without
 * having to be preconfigured.  It also shields the various servers from
 * order-of-startup issues.  Finally, it provides a place to stand for
 * monitoring and administering a Elko server farm as a whole.
 */
@Suppress("unused")
class BrokerBoot : Bootable {
    override fun boot(props: ElkoProperties, gorgel: Gorgel, clock: Clock) {
        val myGorgel = gorgel.getChild(BrokerBoot::class)
        lateinit var brokerServerGraph: BrokerServerOgd.Graph
        val graphClosingShutdownWatcher = ShutdownWatcher { brokerServerGraph.close() }
        brokerServerGraph = BrokerServerOgd(object : BrokerServerOgd.Provided {
            override fun clock() = ConstantDefinition(clock)
            override fun baseGorgel() = ConstantDefinition(gorgel)
            override fun props() = ConstantDefinition(props)
            override fun externalShutdownWatcher() = ConstantDefinition(graphClosingShutdownWatcher)
        }, ObjectGraphConfiguration(object : ObjectGraphLogger {
            override fun errorDuringCleanUp(sourceObject: Any, operation: String, exception: Exception) {
                myGorgel.error(
                    "Error during cleanup of object graph. Object: $sourceObject, operation: $operation",
                    exception
                )
            }
        })).Graph()
        brokerServerGraph.server()
    }
}
