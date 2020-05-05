package org.elkoserver.foundation.net

/**
 * Interface implemented by message handlers that need to preflight received
 * messages prior to putting them on the receive queue.
 */
internal interface MessageAcquirer {
    /**
     * Do whatever needs to be done with a received message.
     *
     * @param message  The message that was received.
     */
    fun acquireMessage(message: Any)
}