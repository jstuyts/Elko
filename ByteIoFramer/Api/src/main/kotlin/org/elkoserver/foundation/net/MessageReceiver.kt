package org.elkoserver.foundation.net

/**
 * Interface for the object that a `Connection` uses to accept incoming
 * messages from the net.
 */
interface MessageReceiver {
    /**
     * Receive a message from elsewhere.
     *
     * @param message  The message to be received.
     */
    fun receiveMsg(message: Any)
}
