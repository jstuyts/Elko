package org.elkoserver.foundation.server

import org.elkoserver.foundation.actor.NonRoutingActor
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc.Companion.encodeArray
import org.elkoserver.json.JSONLiteralFactory.targetVerb
import org.elkoserver.json.Referenceable
import org.elkoserver.util.trace.TraceFactory

/**
 * Actor representing a server's connection to its farm's broker.
 *
 * @param connection  The connection for communicating with the broker.
 * @param dispatcher  Dispatcher for routing messages from the broker.
 * @param myServer  This actor's own server.
 * @param host  The broker's host address.
 */
class BrokerActor(connection: Connection, dispatcher: MessageDispatcher,
                  private val myServer: Server, host: HostDesc, traceFactory: TraceFactory) : NonRoutingActor(connection, dispatcher, traceFactory) {

    /** Load watcher for this actor to report load to the broker.  */
    private val myLoadWatcher: LoadWatcher = object : LoadWatcher {
        override fun noteLoadSample(loadFactor: Double) {
            send(msgLoad(this@BrokerActor, loadFactor))
        }
    }

    /**
     * Handle loss of connection from the broker.
     *
     * @param connection  The broker connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        traceFactory.comm.eventm("lost broker connection $connection: $reason")
        myServer.unregisterLoadWatcher(myLoadWatcher)
        myServer.brokerConnected(null)
    }

    /**
     * Send a request for a service description to the broker.
     *
     * @param service  The service being requested.
     * @param monitor  Should broker continue watching for more results?
     * @param tag  Optional tag to match response with the request.
     */
    fun findService(service: String, monitor: Boolean, tag: String) {
        send(msgFind(this, service, monitor, tag))
    }

    /**
     * Get this object's reference string.  This singleton object's reference
     * string is always 'broker'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "broker"

    /**
     * Register an individual service with this broker.
     *
     * @param service  The service to register.
     */
    fun registerService(service: ServiceDesc?) {
        send(msgWillserve(this, listOf(service)))
    }
    /* ----- JSON method protocol ------------------------------------------ */
    /**
     * Handle a 'find' message: process the result of a previously sent 'find'
     * request.
     *
     * @param desc  The service description(s) returned by the broker.
     * @param tag  The 'tag' string from the request.  (Optional, default "").
     */
    @JSONMethod("desc", "tag")
    fun find(from: BrokerActor, desc: Array<ServiceDesc>, tag: OptString) {
        myServer.foundService(desc, tag.value(""))
    }

    /**
     * Handle a 'reinit' message: reinitialize this server.
     */
    @JSONMethod
    fun reinit(from: BrokerActor?) {
        myServer.reinit()
    }

    /**
     * Handle a 'shutdown' message: shut down this server.
     */
    @JSONMethod
    fun shutdown(from: BrokerActor) {
        myServer.shutdown()
    }

    companion object {
        /* ----- JSON message generators --------------------------------------- */
        /**
         * Create a 'find' message: ask the broker to look up service information.
         * @param target  Object the message is being sent to.
         * @param service  The service being requested.
         * @param monitor  If true, broker should keep watching for additional
         * matches for the requested service.
         * @param tag  Optional tag to match response with the request.
         */
        private fun msgFind(target: Referenceable, service: String, monitor: Boolean, tag: String) =
                targetVerb(target, "find").apply {
                    addParameter("service", service)
                    addParameter("wait", -1)
                    if (monitor) {
                        addParameter("monitor", true)
                    }
                    addParameterOpt("tag", tag)
                    finish()
                }

        /**
         * Create a 'load' message: report this server's load to the broker.
         *
         * @param target  Object the message is being sent to.
         * @param factor  Load factor to report.
         */
        private fun msgLoad(target: Referenceable, factor: Double) =
                targetVerb(target, "load").apply {
                    addParameter("factor", factor)
                    finish()
                }

        /**
         * Create a 'willserve' message: notify the broker that this server is
         * offering one or more services.
         *
         * @param target  Object the message is being sent to.
         * @param services  List of the services being offered.
         */
        private fun msgWillserve(target: Referenceable,
                                 services: List<ServiceDesc?>) =
                targetVerb(target, "willserve").apply {
                    addParameter("services", encodeArray(services))
                    finish()
                }
    }

    init {
        send(msgAuth(this, host.auth, myServer.serverName))
        send(msgWillserve(this, myServer.services()))
        myServer.registerLoadWatcher(myLoadWatcher)
        myServer.brokerConnected(this)
    }
}
