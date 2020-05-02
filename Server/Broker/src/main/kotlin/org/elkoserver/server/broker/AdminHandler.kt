package org.elkoserver.server.broker

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.server.metadata.LoadDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.util.trace.TraceFactory
import java.util.LinkedList

/**
 * Singleton handler for the broker 'admin' protocol.
 *
 * The 'admin' protocol consists of these requests:
 *
 * 'loaddesc' - Requests a description of the load on each of the servers
 * that the broker knows about.
 *
 * 'launch' - Command this broker to startup a new server process.
 *
 * 'launcherdesc' - Request a description of the launchers this broker is
 * configured with.
 *
 * 'reinit' - Requests the broker to order the reinitialization of zero or
 * more of the servers it knows about, and, optionally, itself.
 *
 * 'servicedesc' - Requests a description of available services that the
 * broker knows about, optionally limiting the scope of the query to
 * particular service name classes.
 *
 * 'shutdown' - Requests the broker to order the shut down of zero or more of
 * the servers it knows about, and, optionally, itself.  Also has an option
 * to force abrupt termination.
 *
 * 'watch' - Requests the broker to send, or stop sending, notifications
 * about services and/or load information as information about these
 * arrives from the various servers.
 *
 * @param myBroker  The broker object for this handler.
 */
internal class AdminHandler(private val myBroker: Broker, traceFactory: TraceFactory?) : BasicProtocolHandler(traceFactory) {

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'admin'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "admin"

    /**
     * Send load information to a client.
     *
     * @param who  Who to send the information to.
     * @param what  Server to send them information about, or null for all.
     */
    private fun sendLoadDesc(who: BrokerActor, what: String?) {
        val array = JSONLiteralArray()
        for (actor in myBroker.actors()) {
            val client = actor.client()
            if (client != null) {
                if (what == null || client.matchLabel(what)) {
                    val desc = LoadDesc(actor.label()!!, client.loadFactor(), client.providerID())
                    array.addElement(desc)
                }
            }
        }
        array.finish()
        who.send(msgLoadDesc(this, array))
    }

    /**
     * Send service information to a client.
     *
     * @param who  Who to send the information to.
     * @param service  Service to send them information about, or null for all.
     * @param protocol  Protocol to send service information about.
     */
    private fun sendServiceDesc(who: BrokerActor, service: String?,
                                protocol: String?) {
        val services = myBroker.services(service, protocol!!)
        who.send(msgServiceDesc(this, ServiceDesc.encodeArray(services),
                true))
    }

    /**
     * Handle the 'launch' verb.
     *
     * Request the broker to start another server process by invoking one of
     * the broker's configured launchers.
     *
     * @param from  The administrator who is commanding this.
     * @param name  The name of component launcher configuration
     */
    @JSONMethod("name")
    fun launch(from: BrokerActor, name: String) {
        from.ensureAuthorizedAdmin()
        var status = myBroker.launcherTable()!!.launch(name)
        if (status == null) {
            status = "start $name"
        }
        myBroker.checkpoint()
        from.send(msgLaunch(this, status))
    }

    /**
     * Handle the 'launcherdesc' verb.
     *
     * Request information about the launchers this broker is configured with.
     *
     * @param from  The administrator asking for the information.
     */
    @JSONMethod
    fun launcherdesc(from: BrokerActor) {
        from.ensureAuthorizedAdmin()
        from.send(msgLauncherDesc(this,
                myBroker.launcherTable()!!.encodeAsArray()))
    }

    /**
     * Handle the 'loaddesc' verb.
     *
     * Request information about the load on connected servers.
     *
     * @param from  The administrator asking for the information.
     * @param optServer  The name of the server of interest (or null for all).
     */
    @JSONMethod("server")
    fun loaddesc(from: BrokerActor, optServer: OptString) {
        from.ensureAuthorizedAdmin()
        sendLoadDesc(from, optServer.value<String?>(null))
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Request that one or more connected servers be reset.
     *
     * @param from  The administrator sending the message.
     * @param optServer  The connected server to be re-init'ed ("all" for all of
     * them, or null for none of them).
     * @param optSelf  true if the this broker itself should be re-init'ed.
     */
    @JSONMethod("server", "self")
    fun reinit(from: BrokerActor, optServer: OptString, optSelf: OptBoolean) {
        from.ensureAuthorizedAdmin()
        val serverName = optServer.value<String?>(null)
        if (serverName != null) {
            val msg = msgReinit(myBroker.clientHandler())
            for (actor in myBroker.actors()) {
                val client = actor.client()
                if (client != null) {
                    if (serverName == "all" ||
                            client.matchLabel(serverName)) {
                        actor.send(msg)
                    }
                }
            }
        }
        if (optSelf.value(false)) {
            myBroker.reinitServer()
        }
    }

    /**
     * Handle the 'servicedesc' verb.
     *
     * Request information about connected servers.
     *
     * @param from  The administrator asking for the information.
     * @param service  The name of the service of interest (or null for all).
     * @param protocol The name of the protocol of interest
     */
    @JSONMethod("service", "protocol")
    fun servicedesc(from: BrokerActor, service: OptString, protocol: OptString) {
        from.ensureAuthorizedAdmin()
        sendServiceDesc(from, service.value<String?>(null), protocol.value("tcp"))
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Request that one or more connected servers be shut down.
     *
     * @param from  The administrator sending the message.
     * @param optServer  The connecte server to be shut down, if any ("all" for
     * all of them, null for none of them).
     * @param optSelf  true if this broker itself should be shut down.
     * @param optKill  true if the shutdown should happen immediately without
     * waiting for orderly shutdown to complete.
     * @param optCluster  true if this is part of a cluster shutdown that should
     * not alter component run settings.
     */
    @JSONMethod("server", "self", "kill", "cluster")
    fun shutdown(from: BrokerActor, optServer: OptString, optSelf: OptBoolean, optKill: OptBoolean, optCluster: OptBoolean) {
        from.ensureAuthorizedAdmin()
        val serverName = optServer.value<String?>(null)
        val componentShutdown = !optCluster.value(false)
        var kill = optKill.value(false)
        if (serverName != null) {
            val msg = msgShutdown(myBroker.clientHandler(), kill)
            val actorsToKill: List<BrokerActor> = LinkedList(myBroker.actors())
            for (actor in actorsToKill) {
                val client = actor.client()
                if (client != null) {
                    if (serverName == "all" ||
                            client.matchLabel(serverName)) {
                        if (componentShutdown) {
                            // FIXME: Check if having no launcher table is a state that should be supported.
                            myBroker.launcherTable()!!.setRunSettingOn(serverName)
                        }
                        actor.send(msg)
                    }
                }
            }
            kill = false /* Need to ignore the kill flag in this case, because
                             abrupt shutdown would terminate this server before
                             the shutdown messages to the *other* servers got
                             sent. */
        }
        myBroker.checkpoint()
        if (optSelf.value(false)) {
            myBroker.shutdownServer(kill)
        }
    }

    /**
     * Handle the 'watch' verb.
     *
     * Request notification about changes to information that the broker knows.
     *
     * @param from  The administrator asking for the information.
     * @param services  Flag indicating whether sender wants to be notified
     * about services coming or going (true=>yes, false=>no,
     * omitted=>leave as currently set).
     * @param load  Flag indicating whether sender wants to be notified
     * about server load status changes (true=>yes, false=>no,
     * omitted=>leave as currently set).
     */
    @JSONMethod("services", "load")
    fun watch(from: BrokerActor, services: OptBoolean, load: OptBoolean) {
        from.ensureAuthorizedAdmin()
        if (services.present()) {
            if (services.value()) {
                sendServiceDesc(from, null, null)
                myBroker.watchServices(from)
            } else {
                myBroker.unwatchServices(from)
            }
        }
        if (load.present()) {
            if (load.value()) {
                sendLoadDesc(from, null)
                myBroker.watchLoad(from)
            } else {
                myBroker.unwatchLoad(from)
            }
        }
    }

    companion object {
        /**
         * Generate a 'launch' message.
         */
        private fun msgLaunch(target: Referenceable, status: String) =
                JSONLiteralFactory.targetVerb(target, "launch").apply {
                    addParameter("status", status)
                    finish()
                }

        /**
         * Generate a 'launcherdesc' message.
         */
        private fun msgLauncherDesc(target: Referenceable, launchers: JSONLiteralArray) =
                JSONLiteralFactory.targetVerb(target, "launcherdesc").apply {
                    addParameter("launchers", launchers)
                    finish()
                }

        /**
         * Generate a 'loaddesc' message.
         */
        fun msgLoadDesc(target: Referenceable, desc: JSONLiteralArray?) =
                JSONLiteralFactory.targetVerb(target, "loaddesc").apply {
                    addParameter("desc", desc)
                    finish()
                }

        /**
         * Generate a 'reinit' message.
         */
        private fun msgReinit(target: Referenceable) =
                JSONLiteralFactory.targetVerb(target, "reinit").apply {
                    finish()
                }

        /**
         * Generate a 'servicedesc' message.
         */
        fun msgServiceDesc(target: Referenceable, desc: JSONLiteralArray?, on: Boolean) =
                JSONLiteralFactory.targetVerb(target, "servicedesc").apply {
                    addParameter("desc", desc)
                    addParameter("on", on)
                    finish()
                }

        /**
         * Generate a 'shutdown' message.
         */
        private fun msgShutdown(target: Referenceable, kill: Boolean) =
                JSONLiteralFactory.targetVerb(target, "shutdown").apply {
                    if (kill) {
                        addParameter("kill", kill)
                    }
                    finish()
                }
    }
}
