package org.elkoserver.foundation.server

import org.elkoserver.foundation.server.metadata.ServiceDesc
import java.util.function.Consumer

/**
 * A pending service lookup query to a broker.
 *
 * @param service  The name of the service sought.
 * @param myHandler  Handler to handle results when they arrive.
 * @param isMonitor   If true, continue waiting for more results.
 * @param tag  Optional tag string for matching response with the request.
 */
internal class ServiceQuery(private val service: String, private var myHandler: Consumer<in Array<ServiceDesc>>?, val isMonitor: Boolean, internal val tag: String) {
    /**
     * Handle a result.
     *
     * @param services  Service descriptions that were sent by the broker.
     */
    fun result(services: Array<ServiceDesc>) {
        val currentHandler = myHandler
        if (currentHandler != null) {
            currentHandler.accept(services)
            if (!isMonitor) {
                myHandler = null
            }
        }
    }
}
