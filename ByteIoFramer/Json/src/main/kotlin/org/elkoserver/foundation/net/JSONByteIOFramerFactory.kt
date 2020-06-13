package org.elkoserver.foundation.net

import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory

/**
 * Byte I/O framer factory for JSON messaging over a byte stream.  The framing
 * rule used is: a block of one or more non-empty lines terminated by an empty
 * line (i.e., by two successive newlines).
 *
 * On input, each block matching this framing rule is regarded as a
 * parseable unit; that is, it is expected to contain one or more syntactically
 * complete JSON messages.  The entire block is read into an internal buffer,
 * then parsed for JSON messages that are fed to the receiver.
 *
 * On output, each message being sent is framed according to this rule.
 *
 * @param trMsg  Trace object for logging message traffic.
 */
class JSONByteIOFramerFactory(private val trMsg: Trace, private val traceFactory: TraceFactory, private val mustSendDebugReplies: Boolean) : ByteIOFramerFactory {

    /**
     * Provide an I/O framer for a new connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    override fun provideFramer(receiver: MessageReceiver, label: String) =
            JSONByteIOFramer(trMsg, receiver, label, traceFactory, mustSendDebugReplies)
}
