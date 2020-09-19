package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.server.metadata.AuthDesc

/**
 * Interface to provide the application-specific portion of [ ] creation.
 *
 * An Elko server application provides an implementation of this interface
 * to the [Server] object when it asks it to start listeners based on
 * information in the server configuration file.  Whenever a listener is
 * started, that ServiceFactory is called to provide an appropriate [ ] to provide [ ]s for each new
 * connection accepted by that listener, based on the selection of services
 * that are configured to be offered over connections made to that listener.
 */
interface ServiceFactory {
    /**
     * Provide a message handler factory for a new listener.
     *
     * @param label  The label for the listener; typically this is the root
     * property name for the properties defining the listener attributes.
     * @param auth  The authorization configuration for the listener.
     * @param allow  A set of permission keywords (derived from the properties
     * configuring this listener) that specify what sorts of connections
     * will be permitted through the listener.
     * @param serviceNames  A mutable list to which this method should append
     * (and thus return) the names of the services offered to connections
     * made to the new listener.
     * @param protocol  The protocol (TCP, HTTP, etc.) that connections made
     * to the new listener are expected to speak
     *
     * @return a new [MessageHandlerFactory] that  will provide message
     * handlers for connections made to the new listener.
     */
    fun provideFactory(label: String, auth: AuthDesc,
                       allow: Collection<String>, serviceNames: MutableList<String>, protocol: String): MessageHandlerFactory
}