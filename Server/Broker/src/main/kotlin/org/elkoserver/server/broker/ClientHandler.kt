package org.elkoserver.server.broker

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.util.trace.TraceFactory

/**
 * Singleton handler for the broker client protocol.
 *
 * The client protocol consists of these messages:
 *
 * 'find' - Requests location of a service of particular named type,
 * optionally waiting for the service to become available if one is not
 * available when the request is received, optionally asking to be
 * notified of new services of the specified type as they appear.
 *
 * 'load' - Reports the client's current load factor to the broker.
 *
 * 'willserve' - Reports the client's willingness to provide one or more
 * named services.
 *
 * 'wontserve' - Reports that the client will no longer provide one or more
 * named services.
 *
 * @param myBroker  The broker object for this handler.
 */
internal class ClientHandler(private val myBroker: Broker, traceFactory: TraceFactory?) : BasicProtocolHandler(traceFactory) {

    /**
     * Notify a waiting client that a service it was waiting for has not been
     * found.
     *
     * @param who  The client who was waiting.
     * @param service  The name of the service that was not found.
     * @param tag  Arbitrary tag for matching requests with responses.
     */
    fun findFailure(who: BrokerActor, service: String?, tag: String?) {
        val desc = ServiceDesc(service, "no such service")
        who.send(msgFind(this, desc.encodeAsArray(), tag))
    }

    /**
     * Notify a waiting client that a service it was waiting for has been
     * found.
     *
     * @param who  The client who was waiting.
     * @param service  The service that was found.
     * @param tag  Arbitrary tag for matching requests with responses.
     */
    fun findSuccess(who: BrokerActor, service: ServiceDesc, tag: String?) {
        who.send(msgFind(this, service.encodeAsArray(), tag))
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'broker'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "broker"

    /**
     * Handle the 'find' verb.
     *
     * Locate a service for a client.
     *
     * @param from  The client asking to find a service.
     * @param service  The name of the service they are seeking.
     * @param optWait  Optional time (default 0) to wait for service to become
     * available (0 means don't wait, a negative value means wait forever).
     * @param optMonitor  Optional flag (default false) to keep looking for new
     * services of the requested name as they appear, even after a reply has
     * been return.  A value of true is invalid if 'wait' is 0.
     * @param optTag  Arbitrary tag that will be sent back with the response, to
     * help the client match up requests and responses.
     */
    @JSONMethod("service", "protocol", "wait", "monitor", "tag")
    fun find(from: BrokerActor, service: String, optProtocol: OptString, optWait: OptInteger, optMonitor: OptBoolean, optTag: OptString) {
        val wait = optWait.value(0)
        val monitor = optMonitor.value(false)
        val tag = optTag.value<String?>(null)
        val protocol = optProtocol.value("tcp")
        val services = myBroker.services(service, protocol)
        if (!services.iterator().hasNext()) {
            if (wait == 0) {
                findFailure(from, service, tag)
            } else {
                myBroker.waitForService(service, from, monitor, wait, false, tag)
            }
        } else {
            from.send(msgFind(this, ServiceDesc.encodeArray(services), tag))
            if (monitor) {
                myBroker.waitForService(service, from, true, wait, true, tag)
            }
        }
    }

    /**
     * Handle the 'load' verb.
     *
     * Note a client's load factor.
     *
     * @param from  The client server announcing its load.
     * @param factor  The load factor.
     */
    @JSONMethod("factor")
    fun load(from: BrokerActor, factor: Double) {
        from.ensureAuthorizedClient()
        from.client()!!.setLoadFactor(factor)
        myBroker.noteLoadDesc(from)
    }

    /**
     * Test if a service description record what was sent correctly describes a
     * service.
     *
     * @param service  The service to validate.
     *
     * @return true if the given service has a valid description, false if not.
     */
    private fun validServiceDescription(service: ServiceDesc): Boolean {
        return service.hostport() != null && service.failure() == null
    }

    /**
     * Handle the 'willserve' verb.
     *
     * Announce a client willingness to provide one or more services.
     *
     * @param from  The client server announcing its services.
     * @param services  Description(s) of the service(s) offered.
     */
    @JSONMethod("services")
    fun willserve(from: BrokerActor, services: Array<ServiceDesc>) {
        from.ensureAuthorizedClient()
        for (service in services) {
            if (validServiceDescription(service)) {
                from.client()!!.addService(service)
            }
        }
    }

    /**
     * Handle the 'wontserve' verb.
     *
     * Announce a client server's ceasing to provide one or more services.
     *
     * @param from  The client server retracting its services.
     * @param services  Name(s) of the service(s) no longer offered.
     */
    @JSONMethod("services")
    fun wontserve(from: BrokerActor, services: Array<String?>?) {
        from.ensureAuthorizedClient()
        if (services != null) {
            for (service in services) {
                from.client()!!.removeService(service!!)
            }
        }
    }

    companion object {
        /**
         * Generate a 'find' message.
         */
        private fun msgFind(target: Referenceable, desc: JSONLiteralArray, tag: String?) =
                JSONLiteralFactory.targetVerb(target, "find").apply {
                    addParameter("desc", desc)
                    addParameterOpt("tag", tag)
                    finish()
                }
    }
}
