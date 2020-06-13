package org.elkoserver.server.context

import org.elkoserver.foundation.actor.Actor
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.ConnectionRetrier
import org.elkoserver.foundation.net.MessageHandler
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.ReinitWatcher
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag
import java.time.Clock
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
 * @param tr  Trace object for diagnostics.
 */
abstract class OutboundGroup(propRoot: String,
                             private val myServer: Server,
                             internal val contextor: Contextor,
                             hosts: MutableList<HostDesc>,
                             private val tr: Trace,
                             gorgel: Gorgel,
                             private val connectionRetrierWithoutLabelGorgel: Gorgel,
                             protected val timer: Timer,
                             protected val traceFactory: TraceFactory,
                             clock: Clock,
                             props: ElkoProperties,
                             jsonToObjectDeserializer: JsonToObjectDeserializer,
                             private val mustSendDebugReplies: Boolean) : LiveGroup() {
    /** The statically configured external servers in this group.  */
    private val myHosts: List<HostDesc>

    /** Flag that the external servers should be located via the broker.  */
    private val amAutoRegister: Boolean

    /** Network manager for making new outbound connections.  */
    private val myNetworkManager: NetworkManager

    /** Message dispatcher for incoming messages on these connections.  */
    private val myDispatcher: MessageDispatcher

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
            ConnectionRetrier(host, label(), myNetworkManager,
                    HostConnector(host), timer, connectionRetrierWithoutLabelGorgel.withAdditionalStaticTags(Tag("label", label())), tr, traceFactory, mustSendDebugReplies)
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
                        ConnectionRetrier(it, label(), myNetworkManager,
                                HostConnector(it), timer, connectionRetrierWithoutLabelGorgel.withAdditionalStaticTags(Tag("label", label())), tr, traceFactory, mustSendDebugReplies)
                    }
        }
    }

    /**
     * Factory class to hold onto host information while attempting to
     * establish an external server connection.
     */
    private inner class HostConnector internal constructor(private val myHost: HostDesc) : MessageHandlerFactory {

        /**
         * Provide a message handler for a new external server connection.
         *
         * @param connection  The Connection object that was just created.
         */
        override fun provideMessageHandler(connection: Connection?): MessageHandler = provideActor(connection!!, myDispatcher, myHost)

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
    abstract fun provideActor(connection: Connection, dispatcher: MessageDispatcher, host: HostDesc): Actor

    /**
     * Obtain a broker service string describing the type of service that
     * connections in this group want to connect to.
     *
     * @return a broker service string for this group.
     */
    internal abstract fun service(): String

    init {
        myServer.registerReinitWatcher(object : ReinitWatcher {
            override fun noteReinit() {
                disconnectHosts()
                connectHosts()
            }
        })
        myNetworkManager = myServer.networkManager
        myHosts = hosts
        myDispatcher = MessageDispatcher(null, traceFactory, clock, jsonToObjectDeserializer)
        @Suppress("LeakingThis")
        myDispatcher.addClass(actorClass())
        amAutoRegister = props.testProperty("$propRoot.auto")
        myRetryInterval = props.intProperty("$propRoot.retry", -1)
        val iter = hosts.iterator()
        while (iter.hasNext()) {
            val host = iter.next()
            if (host.protocol != "tcp") {
                // FIXME: Do not modify constructor parameters. Pass the correct value instead. Validate if needed
                iter.remove()
                gorgel.error("unknown $propRoot server access protocol '${host.protocol}' for access to ${host.hostPort} (configuration ignored)")
            }
        }
    }
}
