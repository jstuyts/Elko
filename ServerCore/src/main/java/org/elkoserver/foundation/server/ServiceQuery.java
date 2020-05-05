package org.elkoserver.foundation.server;

import org.elkoserver.foundation.server.metadata.ServiceDesc;

import java.util.function.Consumer;

/**
 * A pending service lookup query to a broker.
 */
class ServiceQuery {
    /** The service name that was requested. */
    private final String myService;

    /** Handler for results when and if they arrive. */
    private Consumer<? super ServiceDesc[]> myHandler;

    /** Flag to continue waiting for further results. */
    private final boolean amMonitor;

    /** Tag ID string for matching results to who asked for them. */
    private final String myTag;

    /**
     * Constructor.
     *
     * @param service  The name of the service sought.
     * @param handler  Handler to handle results when they arrive.
     * @param isMonitor   If true, continue waiting for more results.
     * @param tag  Optional tag string for matching response with the request.
     */
    ServiceQuery(String service, Consumer<? super ServiceDesc[]> handler, boolean isMonitor, String tag)
    {
        myService = service;
        myHandler = handler;
        amMonitor = isMonitor;
        myTag = tag;
    }

    /**
     * Test if this is an ongoing query.
     *
     * @return true if this query continues to wait for more results.
     */
    boolean isMonitor() {
        return amMonitor;
    }

    /**
     * Handle a result.
     *
     * @param services  Service descriptions that were sent by the broker.
     */
    void result(ServiceDesc[] services) {
        if (myHandler != null) {
            myHandler.accept(services);
            if (!amMonitor) {
                myHandler = null;
            }
        }
    }

    /**
     * Get the service that was requested.
     *
     * @return the name of the service sought.
     */
    String service() {
        return myService;
    }

    /**
     * Return the tag ID string.
     *
     * @return the tag string for the request that was sent.
     */
    String tag() {
        return myTag;
    }
}

