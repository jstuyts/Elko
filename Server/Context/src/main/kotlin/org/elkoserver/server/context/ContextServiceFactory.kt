package org.elkoserver.server.context

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.ServiceFactory
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.slf4j.Gorgel

internal class ContextServiceFactory(
        private val gorgel: Gorgel,
        private val internalActorFactoryFactory: InternalActorFactoryFactory,
        private val userActorFactoryFactory: UserActorFactoryFactory) : ServiceFactory {
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
    override fun provideFactory(label: String, auth: AuthDesc, allow: Collection<String>, serviceNames: MutableList<String>, protocol: String): MessageHandlerFactory {
        return if (allow.contains("internal")) {
            serviceNames.add("context-internal")
            internalActorFactoryFactory.create(auth)
        } else {
            val reservationRequired: Boolean =
                    when (auth.mode) {
                        "open" -> false
                        "reservation" -> true
                        else -> {
                            gorgel.error("invalid authorization configuration for $label")
                            throw IllegalStateException()
                        }
                    }
            serviceNames.add("context-user")
            userActorFactoryFactory.create(reservationRequired, protocol)
        }
    }
}
