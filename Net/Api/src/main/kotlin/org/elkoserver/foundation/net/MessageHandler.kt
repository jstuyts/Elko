package org.elkoserver.foundation.net

/**
 * Interface for objects that handle events on a [Connection].
 *
 * An implementor of this interface is associated with each [Connection]
 * object, to handle both incoming messages and disconnection events.
 * Normally, a [Connection]'s MessageHandler is produced by a [ ] when the connection is established.  The factory is
 * held by the [NetworkManager] for this purpose.
 */
interface MessageHandler {
    /**
     * Cope with connection death.  The connection might have been shut down
     * deliberately, the underlying TCP connection might have failed, or an
     * internal error may have closed the connection.  In any event, the
     * connection is dead.  Deal with it.
     *
     * @param connection  The connection that has just died.
     * @param reason  A possible indication why the connection went away.
     */
    fun connectionDied(connection: Connection, reason: Throwable)

    /**
     * Process an incoming message from a connection.
     *
     * @param connection  The connection upon which the message arrived.
     * @param message  The incoming message.
     */
    fun processMessage(connection: Connection, message: Any)
}
