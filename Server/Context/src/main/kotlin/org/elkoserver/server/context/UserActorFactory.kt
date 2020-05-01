package org.elkoserver.server.context

import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory

/**
 * MessageHandlerFactory class to associate new Users with new Connections.
 *
 * @param myContextor  The contextor for this server.
 * @param amAuthRequired  Flag indicating whether reservations are
 *    needed for entry.
 * @param myProtocol  Protocol these new connections will be speaking
 * @param tr  Trace object for diagnostics.
 */
internal class UserActorFactory(private val myContextor: Contextor, private val amAuthRequired: Boolean,
                                private val myProtocol: String, private val tr: Trace, private val timer: Timer, private val traceFactory: TraceFactory) : MessageHandlerFactory {

    /**
     * Produce a new user for a new connection.
     *
     * @param connection  The new connection.
     */
    override fun provideMessageHandler(connection: Connection) =
            UserActor(connection, myContextor, amAuthRequired, myProtocol, tr, timer, traceFactory)
}
