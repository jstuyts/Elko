package org.elkoserver.foundation.net.zmq

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.NullMessageHandler
import org.elkoserver.server.context.BasicInternalObject
import org.elkoserver.server.context.Contextor

/**
 * Static object establish and hold onto a ZMQ outbound connection.
 *
 * @param myAddress  Address the outbound ZMQ connection should connect to.
 */
class ZMQOutbound @JSONMethod("address") constructor(private val myAddress: String) : BasicInternalObject() {

    /**
     * Obtain the outbound connection.
     *
     * @return a Connection object suitable for sending JSON messages on the
     * outbound ZMQ connection.
     */
    /** Outbound ZMQ connection to myAddress, or null if not yet open  */
    var connection: Connection? = null
        private set

    /**
     * Make this object live inside the context server.
     *
     * @param ref  Reference string identifying this object in the static
     * object table.
     * @param contextor  The contextor for this server.
     */
    override fun activate(ref: String, contextor: Contextor) {
        super.activate(ref, contextor)
        contextor.server().networkManager().connectVia(
                "org.elkoserver.foundation.net.zmq.ZeroMQConnectionManager",
                "",
                myAddress,
                { conn: Connection? ->
                    connection = conn
                    NullMessageHandler(contextor.appTrace())
                },
                contextor.appTrace())
    }
}
