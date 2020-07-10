package org.elkoserver.foundation.byteioframer

/**
 * Interface supporting protocol-specific message framing on a connection,
 * to frame inchoate streams of bytes into processable units.
 */
interface ByteIoFramerFactory {
    /**
     * Provide an I/O framer for a new connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    fun provideFramer(receiver: MessageReceiver, label: String): ByteIoFramer
}
