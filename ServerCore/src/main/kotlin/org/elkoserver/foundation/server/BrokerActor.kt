package org.elkoserver.foundation.server

import org.elkoserver.foundation.actor.NonRoutingActor
import org.elkoserver.foundation.actor.msgAuth
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Actor representing a server's connection to its farm's broker.
 *
 * @param connection  The connection for communicating with the broker.
 * @param dispatcher  Dispatcher for routing messages from the broker.
 * @param myServer  This actor's own server.
 */
class BrokerActor(
    connection: Connection,
    dispatcher: MessageDispatcher,
    private val myServer: Server,
    private val loadMonitor: ServerLoadMonitor,
    private val shutdownWatcher: ShutdownWatcher,
    auth: AuthDesc,
    gorgel: Gorgel,
    mustSendDebugReplies: Boolean
) : NonRoutingActor(connection, dispatcher, gorgel, mustSendDebugReplies) {

    /** Load watcher for this actor to report load to the broker.  */
    private val myLoadWatcher: LoadWatcher =
        LoadWatcher { loadFactor -> send(msgLoad(this@BrokerActor, loadFactor)) }

    /**
     * Handle loss of connection from the broker.
     *
     * @param connection  The broker connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        gorgel.i?.run { info("lost broker connection $connection: $reason") }
        loadMonitor.unregisterLoadWatcher(myLoadWatcher)
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
    override fun ref(): String = "broker"

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
    @JsonMethod("desc", "tag")
    fun find(from: BrokerActor, desc: Array<ServiceDesc>, tag: OptString) {
        myServer.foundService(desc, tag.value(""))
    }

    /**
     * Handle a 'reinit' message: reinitialize this server.
     */
    @JsonMethod
    fun reinit(from: BrokerActor?) {
        myServer.reinit()
    }

    /**
     * Handle a 'shutdown' message: shut down this server.
     */
    @JsonMethod
    fun shutdown(from: BrokerActor) {
        shutdownWatcher.noteShutdown()
    }

    init {
        send(msgAuth(this, auth, myServer.serverName))
        send(msgWillserve(this, myServer.services()))
        loadMonitor.registerLoadWatcher(myLoadWatcher)
        myServer.brokerConnected(this)
    }
}
