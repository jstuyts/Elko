package org.elkoserver.foundation.server.metadata

import java.util.function.Consumer

/**
 * Interface for using the broker to find external services.
 */
interface ServiceFinder {
    /**
     * Issue a request for service information to the broker.
     *
     * @param service  The service desired.
     * @param handler  Object to receive the asynchronous result(s).
     * @param monitor  If true, keep watching for more results after the first.
     */
    fun findService(service: String?, handler: Consumer<Any?>?, monitor: Boolean)
}
