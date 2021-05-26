package org.elkoserver.server.context

import org.elkoserver.foundation.actor.Actor
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.server.context.model.LiveGroup
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.function.Consumer

/**
 * Live group containing a bundle of related connections to some species of
 * external servers.
 *
 * @param propRoot  Prefix string for names of config properties pertaining
 *    to this group
 * @param myServer  Server object.
 * @param contextor  The server contextor.
 * @param hosts  List of HostDesc objects describing external
 *    servers with whom to register.
 */
abstract class OutboundGroup(propRoot: String,
                             private val myServer: Server,
                             internal val contextor: Contextor,
                             hosts: List<HostDesc>,
                             gorgel: Gorgel,
                             private val myDispatcher: MessageDispatcher,
                             protected val timer: Timer,
                             props: ElkoProperties,
                             private val connectionRetrierFactory: ConnectionRetrierFactory) : LiveGroup() {
    /** The statically configured external servers in this group.  */
    private val myHosts: List<HostDesc>

    /** Flag that the external servers should be located via the broker.  */
    private val amAutoRegister: Boolean

    /** How often to retry connections, in seconds, or -1 for the default.  */
    private val myRetryInterval: Int

    /**
     * Obtain the class of actors in this group.
     *
     * @return this group's actor class.
     */
    abstract fun actorClass(): Class<*>

    /**
     * Open connections to statically configured external servers, try to find
     * out about dynamically configured ones.
     */
    fun connectHosts() {
        for (host in myHosts) {
            connectionRetrierFactory.create(host, label(), HostConnector(host.auth))
        }
        if (amAutoRegister) {
            myServer.findService(service(), HostFoundHandler(), true)
        }
    }

    private inner class HostFoundHandler : Consumer<Array<ServiceDesc>> {
        /**
         * Open connections to external servers configured via the broker.
         *
         * @param obj  Array of service description objects describing external
         * servers to connect to.
         */
        override fun accept(obj: Array<ServiceDesc>) {
            obj
                    .filter { it.failure == null }
                    .map { it.asHostDesc(myRetryInterval) }
                    .forEach {
                        connectionRetrierFactory.create(it, label(), HostConnector(it.auth))
                    }
        }
    }

    /**
     * Factory class to hold onto host information while attempting to
     * establish an external server connection.
     */
    private inner class HostConnector(private val myAuth: AuthDesc) : MessageHandlerFactory {

        /**
         * Provide a message handler for a new external server connection.
         *
         * @param connection  The Connection object that was just created.
         */
        override fun provideMessageHandler(connection: Connection): MessageHandler = provideActor(connection, myDispatcher, myAuth)

        override fun handleConnectionFailure() {
            // No action needed. This factory ignores failures.
        }
    }

    /**
     * Close connections to all open external servers.
     */
    fun disconnectHosts() {
        members()
                .map { it as Actor }
                .forEach(Actor::close)
    }

    /**
     * Test if this group is live, that is, if it corresponds to any actual
     * connections, real or potential.
     *
     * @return true iff there are (or will be) any connections associated with
     * this group.
     */
    val isLive: Boolean
        get() = myHosts.isNotEmpty() || amAutoRegister

    /**
     * Obtain a printable string suitable for tagging this group in log
     * messages and so forth.
     *
     * @return this group's label string.
     */
    abstract fun label(): String

    /**
     * Get an actor object suitable to act on message traffic on a new
     * connection in this group.
     *
     * @param connection  The new connection
     * @param dispatcher   Message dispatcher for the message protocol on the
     * new connection
     * @param host  Descriptor information for the host the new connection is
     * connected to
     *
     * @return a new Actor object for use on this new connection
     */
    abstract fun provideActor(connection: Connection, dispatcher: MessageDispatcher, auth: AuthDesc): Actor

    /**
     * Obtain a broker service string describing the type of service that
     * connections in this group want to connect to.
     *
     * @return a broker service string for this group.
     */
    internal abstract fun service(): String

    init {
        myServer.registerReinitWatcher {
            disconnectHosts()
            connectHosts()
        }

        hosts.filter { it.protocol != "tcp" }.forEach { gorgel.error("unknown $propRoot server access protocol '${it.protocol}' for access to ${it.hostPort} (configuration ignored)") }
        myHosts = hosts.filter { it.protocol == "tcp" }

        @Suppress("LeakingThis")
        myDispatcher.addClass(actorClass())

        amAutoRegister = props.testProperty("$propRoot.auto")
        myRetryInterval = props.intProperty("$propRoot.retry", -1)
    }
}
