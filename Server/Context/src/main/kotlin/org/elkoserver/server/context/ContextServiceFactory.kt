package org.elkoserver.server.context

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.IdGenerator
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

class ContextServiceFactory(
        private val myContextor: Contextor,
        private val gorgel: Gorgel,
        private val internalActorGorgel: Gorgel,
        private val userActorGorgel: Gorgel,
        private val userGorgelWithoutRef: Gorgel,
        private val traceFactory: TraceFactory,
        private val timer: Timer,
        private val idGenerator: IdGenerator) : ServiceFactory {
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
    override fun provideFactory(label: String, auth: AuthDesc, allow: Set<String>, serviceNames: MutableList<String>, protocol: String): MessageHandlerFactory {
        return if (allow.contains("internal")) {
            serviceNames.add("context-internal")
            InternalActorFactory(myContextor, auth, internalActorGorgel, traceFactory)
        } else {
            val reservationRequired: Boolean = when {
                auth.mode() == "open" -> false
                auth.mode() == "reservation" -> true
                else -> {
                    gorgel.error("invalid authorization configuration for $label")
                    throw IllegalStateException()
                }
            }
            serviceNames.add("context-user")
            UserActorFactory(myContextor, reservationRequired, protocol, userActorGorgel, userGorgelWithoutRef, timer, traceFactory, idGenerator)
        }
    }
}