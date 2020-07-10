package org.elkoserver.foundation.net

/**
 * Interface to handle the application-specific portion of [Connection]
 * creation.
 *
 * An object that implements this interface is given to the [ ] when it is asked to start a listener or to create an outbound connection.  When a connection is made, the
 * MessageHandlerFactory will be asked to provide a [MessageHandler] to
 * handle events on the new connection.
 */
interface MessageHandlerFactory {
    /**
     * Provide a message handler for a new connection.
     *
     * Note: in the case of attempting to establish an outbound connection,
     * this method also acts as a callback to notify the application that the
     * connection is now established or that the attempt to establish it
     * failed.  In the latter case, the connection parameter passed will be
     * null.  However, a null connection value will never be passed as part of
     * the handling of an inbound connection, even if that inbound connection
     * failed, so it is only necessary to check for the null case if the
     * specific implementation of MessageHandlerFactory may be used for
     * outbound connections (i.e., usually not).
     *
     * @param connection  The connection that was just created, or null if
     * the attempt to create the connection failed.
     */
    // FIXME: Many implementations expect a connection. Is that correct?
    fun provideMessageHandler(connection: Connection?): MessageHandler?
}