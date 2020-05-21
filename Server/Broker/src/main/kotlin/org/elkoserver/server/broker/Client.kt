package org.elkoserver.server.broker

import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.util.HashMapMulti

/**
 * The client facet of a broker actor.  This object represents the state
 * functionality required when a connected entity is engaging in the client
 * protocol.
 *
 * @param myBroker  The broker whose client this is.
 * @param myActor  The actor associated with the client.
 */
internal class Client(private val myBroker: Broker, private val myActor: BrokerActor) {

    /**
     * Client load factor.
     */
    internal var loadFactor = 0.0
        set(value) {
            field = value.coerceAtLeast(0.0)
        }

    /**
     * Services offered by this client.
     */
    private val myServices = HashMapMulti<String, ServiceDesc>()

    /**
     * Provider ID associated with this client.
     */
    internal val providerID = theNextProviderID++

    /**
     * Add a service to the list for this client.
     *
     * @param service Description of the service to add.
     */
    fun addService(service: ServiceDesc) {
        service.providerID = providerID
        myServices.add(service.service, service)
        myBroker.addService(service)
    }

    /**
     * Clean up when the client actor disconnects.
     */
    fun doDisconnect() {
        for (service in services()) {
            myBroker.removeService(service)
        }
    }

    /**
     * Test if a given label matches this client.
     *
     *
     * This will be true if the label is this client's label or one of its
     * host+port strings or its provider ID.
     *
     * @param label The label to match against.
     */
    fun matchLabel(label: String): Boolean {
        if (myActor.label == label) {
            return true
        }
        for (service in myServices.values()) {
            if (service.hostport == label) {
                return true
            }
        }
        try {
            if (label.toInt() == providerID) {
                return true
            }
        } catch (e: NumberFormatException) {
        }
        return false
    }

    /**
     * Remove a (group of) service(s) from the list for this client.
     *
     * @param serviceName Name of the service(s) to remove.
     */
    fun removeService(serviceName: String) {
        for (service in myServices.getMulti(serviceName)) {
            myBroker.removeService(service)
        }
        myServices.remove(serviceName)
    }

    /**
     * Return an iterable for the set of services this provider supports.
     */
    private fun services() = myServices.values()

    companion object {
        /**
         * Counter for allocating provider IDs.  Starts with 1 because ID 0 is
         * reserved for the broker itself.
         */
        @Deprecated("Global variable")
        private var theNextProviderID = 1
    }
}
