package org.elkoserver.foundation.server

import org.elkoserver.foundation.server.metadata.ServiceDesc
import java.util.function.Consumer

/**
 * A pending service lookup query to a broker.
 *
 * @param myService  The name of the service sought.
 * @param myHandler  Handler to handle results when they arrive.
 * @param isMonitor   If true, continue waiting for more results.
 * @param myTag  Optional tag string for matching response with the request.
 */
internal class ServiceQuery(private val myService: String, private var myHandler: Consumer<in Array<ServiceDesc>>?, val isMonitor: Boolean, private val myTag: String) {
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

    /**
     * Get the service that was requested.
     *
     * @return the name of the service sought.
     */
    fun service() = myService

    /**
     * Return the tag ID string.
     *
     * @return the tag string for the request that was sent.
     */
    fun tag() = myTag
}
