package org.elkoserver.server.context

import org.elkoserver.foundation.boot.Bootable
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.time.Clock
import java.util.*

/**
 * The Elko boot class for the Context Server, the basis of Elko applications
 * built on the context/user/item object model.
 */
class ContextServerBoot : Bootable {
    private lateinit var traceFactory: TraceFactory
    private lateinit var tr: Trace
    private lateinit var timer: Timer
    private var myContextor: Contextor? = null
    override fun boot(props: ElkoProperties, traceFactory: TraceFactory, clock: Clock) {
        this.traceFactory = traceFactory
        tr = traceFactory.trace("cont")
        this.timer = Timer(traceFactory, clock)
        val server = Server(props, "context", tr, timer, clock, traceFactory)
        myContextor = Contextor(server, tr, timer, traceFactory)
        if (server.startListeners("conf.listen",
                        ContextServiceFactory()) == 0) {
            tr.fatalError("no listeners specified")
        }
        val directors = scanHostList(props, "conf.register")
        myContextor!!.registerWithDirectors(directors, server.listeners())
        val presencers = scanHostList(props, "conf.presence")
        myContextor!!.registerWithPresencers(presencers)
    }

    private inner class ContextServiceFactory : ServiceFactory {
        /**
         * Provide a message handler factory for a new listener.
         *
         * @param label  The label for the listener; typically this is the root
         * property name for the properties defining the listener attributes
         * @param auth  The authorization configuration for the listener.
         * @param allow  A set of permission keywords (derived from the
         * properties configuring this listener) that specify what sorts of
         * connections will be permitted through the listener.
         * @param serviceNames  A linked list to which this message should
         * append the names of the services offered by the new listener.
         * @param protocol  The protocol (TCP, HTTP, etc.) that connections
         * made to the new listener are expected to speak
         */
        override fun provideFactory(label: String,
                                    auth: AuthDesc,
                                    allow: Set<String>,
                                    serviceNames: MutableList<String>,
                                    protocol: String): MessageHandlerFactory? {
            return if (allow.contains("internal")) {
                serviceNames.add("context-internal")
                InternalActorFactory(myContextor, auth, tr, traceFactory)
            } else {
                val reservationRequired: Boolean = when {
                    auth.mode() == "open" -> false
                    auth.mode() == "reservation" -> true
                    else -> {
                        tr.errorm("invalid authorization configuration for $label")
                        return null
                    }
                }
                serviceNames.add("context-user")
                UserActorFactory(myContextor, reservationRequired, protocol, tr, timer, traceFactory)
            }
        }
    }

    /**
     * Scan a collection of host descriptors from the server's configured
     * property info.
     *
     * @param props  Properties, from the command line and elsewhere.
     * @param propRoot  Prefix string for props describing the host set of
     * interest
     *
     * @return a list of host descriptors for the configured collection of host
     * information extracted from the properties.
     */
    private fun scanHostList(props: ElkoProperties, propRoot: String): List<HostDesc> {
        var index = 0
        val hosts: MutableList<HostDesc> = LinkedList()
        while (true) {
            var hostPropRoot = propRoot
            if (index > 0) {
                hostPropRoot += index
            }
            ++index
            val host = HostDesc.fromProperties(props, hostPropRoot, traceFactory)
            if (host == null) {
                return hosts
            } else {
                hosts.add(host)
            }
        }
    }
}
