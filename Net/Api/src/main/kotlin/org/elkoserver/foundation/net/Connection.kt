package org.elkoserver.foundation.net

/**
 * A communications connection to other entities on the net.
 */
interface Connection {
    /**
     * Shut down the connection.  Any queued messages will be sent.
     */
    fun close()

    /**
     * Identify this connection for logging purposes.
     *
     * @return this connection's ID number.
     */
    fun id(): Int

    /**
     * Send a message over the connection to whomever is at the other end.
     *
     * @param message  The message to be sent.
     */
    fun sendMsg(message: Any)

    /**
     * Turn debug features for this connection on or off.
     *
     * @param mode  If true, turn debug mode on; if false, turn it off.
     */
    fun setDebugMode(mode: Boolean)
}
