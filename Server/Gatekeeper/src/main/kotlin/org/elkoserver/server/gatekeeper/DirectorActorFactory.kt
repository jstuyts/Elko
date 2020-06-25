package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrier
import org.elkoserver.foundation.net.tcp.client.TcpClientFactory
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag
import java.util.function.Consumer

/**
 * Object to manage the connection to the director.  At any given time, there
 * is a most one director connection.  However, what director is connected
 * can change over time.
 *
 * @param gatekeeper  The gatekeeper.
 * @param tr  Trace object for diagnostics.
 */
internal class DirectorActorFactory(
        private val tcpClientFactory: TcpClientFactory,
        internal val gatekeeper: Gatekeeper,
        private val gorgel: Gorgel,
        private val connectionRetrierWithoutLabelGorgel: Gorgel,
        private val directorActorGorgel: Gorgel,
        methodInvokerCommGorgel: Gorgel,
        private val jsonByteIOFramerGorgel: Gorgel,
        private val tr: Trace,
        private val timer: Timer,
        private val inputGorgel: Gorgel,
        jsonToObjectDeserializer: JsonToObjectDeserializer,
        private val mustSendDebugReplies: Boolean) : MessageHandlerFactory {
    /** Descriptor for the director host.  */
    private var myDirectorHost: HostDesc? = null

    /** The active director connection, if there is one.  */
    private var myDirector: DirectorActor? = null
    private val myDispatcher = MessageDispatcher(null, methodInvokerCommGorgel, jsonToObjectDeserializer)

    /** Object currently attempting to establish a director connection.  */
    private var myConnectionRetrier: ConnectionRetrier? = null

    /**
     * Open connection to the director.
     *
     * @param director  Host and port of director to open a connection to.
     */
    fun connectDirector(director: HostDesc) {
        if (director.protocol != "tcp") {
            gorgel.error("unknown director access protocol '${director.protocol}' for access to ${director.hostPort}")
        } else {
            myConnectionRetrier?.giveUp()
            myDirectorHost = director
            myConnectionRetrier = ConnectionRetrier(
                    director,
                    "director",
                    tcpClientFactory,
                    this,
                    timer,
                    connectionRetrierWithoutLabelGorgel.withAdditionalStaticTags(Tag("label", "director")),
                    jsonByteIOFramerGorgel,
                    tr,
                    inputGorgel,
                    mustSendDebugReplies)
        }
    }

    /**
     * Close connection to the open director.
     */
    fun disconnectDirector() {
        myDirector?.close()
    }

    /**
     * Provide a Message handler for a new director connection.
     *
     * @param connection  The Connection object that was just created.
     */
    override fun provideMessageHandler(connection: Connection?) =
            DirectorActor(connection!!, myDispatcher, this, myDirectorHost!!, directorActorGorgel, mustSendDebugReplies)

    /**
     * Issue a reservation request to the director.
     *
     * @param protocol  The protocol for the requested reservation.
     * @param context  The requested context.
     * @param actor  The requested actor.
     * @param handler  Object to handle result.
     */
    fun requestReservation(protocol: String, context: String, actor: String, handler: Consumer<in ReservationResult>) {
        val currentDirector = myDirector
        if (currentDirector == null) {
            handler.accept(ReservationResult(context, actor, "no director available"))
        } else {
            currentDirector.requestReservation(protocol, context, actor, handler)
        }
    }

    /**
     * Set the active director connection.
     *
     * @param director  The (new) active director.
     */
    fun setDirector(director: DirectorActor?) {
        if (myDirector != null && director != null) {
            gorgel.error("setting director when director already set")
        }
        myDirector = director
    }

    init {
        myDispatcher.addClass(DirectorActor::class.java)
    }
}
