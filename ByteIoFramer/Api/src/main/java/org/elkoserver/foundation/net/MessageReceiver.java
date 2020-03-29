package org.elkoserver.foundation.net;

/**
 * Interface for the object that a <code>Connection</code> uses to accept incoming
 * messages from the net.
 */
public interface MessageReceiver {
    /**
     * Receive a message from elsewhere.
     *
     * @param message  The message to be received.
     */
    void receiveMsg(Object message);
}
