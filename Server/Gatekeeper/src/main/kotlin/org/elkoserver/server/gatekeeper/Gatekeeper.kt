package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.actor.BasicProtocolActor
import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.time.Clock
import java.util.function.Consumer

/**
 * The Gatekeeper itself as presented to its configured [Authorizer]
 * object.  This is the [Authorizer]'s linkage back to the systems that
 * do most of the real work in the Gatekeeper.
 */
class Gatekeeper internal constructor(
        private val myServer: Server,
        private val gorgel: Gorgel,
        directorActorFactoryGorgel: Gorgel,
        connectionRetrierWithoutLabelGorgel: Gorgel,
        tr: Trace,
        timer: Timer,
        traceFactory: TraceFactory,
        clock: Clock,
        hostDescFromPropertiesFactory: HostDescFromPropertiesFactory,
        props: ElkoProperties,
        jsonToObjectDeserializer: JsonToObjectDeserializer) {
    /** Table for mapping object references in messages.  */
    internal val refTable: RefTable = RefTable(null, traceFactory, clock, jsonToObjectDeserializer)

    /** Host description for the director.  */
    internal var directorHost: HostDesc? = null
        private set

    /** Retry interval for connection to director, in seconds, or -1 to take
     * the default.  */
    private val myRetryInterval: Int

    /** Object for managing director connections.  */
    private val myDirectorActorFactory: DirectorActorFactory

    private inner class DirectorFoundRunnable : Consumer<Array<ServiceDesc>> {
        override fun accept(obj: Array<ServiceDesc>) {
            if (obj[0].failure != null) {
                gorgel.error("unable to find director: ${obj[0].failure}")
            } else {
                setDirectorHost(obj[0].asHostDesc(myRetryInterval))
            }
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do admin operations.
     *
     * @throws MessageHandlerException if this actor is not authorized to
     * perform administrative operations.
     */
    fun ensureAuthorizedAdmin(from: BasicProtocolActor) {
        if (from is GatekeeperActor) {
            from.ensureAuthorizedAdmin()
        } else {
            from.doDisconnect()
            throw MessageHandlerException("actor $from attempted admin operation without authorization")
        }
    }

    /**
     * Reinitialize the server.
     */
    fun reinit() {
        myServer.reinit()
    }

    /**
     * Issue a request for a reservation to the Director.
     *
     * @param protocol  The protocol for the requested reservation (i.e., the
     * protocol that the client wishes to speak to the server providing the
     * context).
     * @param context  The requested context.
     * @param actor  The requested actor.
     * @param handler  Object to handle the reservation result.  When the
     * result becomes available, it will be passed as an instance of [    ].
     */
    fun requestReservation(protocol: String, context: String, actor: String, handler: Consumer<in ReservationResult>) {
        if (directorHost == null) {
            handler.accept(ReservationResult(context, actor, "no director host specified"))
        } else {
            myDirectorActorFactory.requestReservation(protocol, context, actor, handler)
        }
    }

    /**
     * Get the Gatekeeper's name.  This can be useful in diagnostic log
     * messages and such.
     *
     * @return the server's name.
     */
    fun serverName() = myServer.serverName

    /**
     * Change the director to which this gatekeeper is connected.  This
     * includes disconnecting from the current director (if one is connected)
     * and connecting to the new one.
     *
     * @param host  The new director host.
     */
    fun setDirectorHost(host: HostDesc) {
        if (directorHost != null) {
            myDirectorActorFactory.disconnectDirector()
        }
        directorHost = host
        myDirectorActorFactory.connectDirector(host)
    }

    /**
     * Shutdown the server.
     */
    fun shutdown() {
        myServer.shutdown()
    }

    init {
        refTable.addRef(AdminHandler(this, traceFactory))
        myDirectorActorFactory = DirectorActorFactory(myServer.networkManager, this, directorActorFactoryGorgel, connectionRetrierWithoutLabelGorgel, tr, timer, traceFactory, clock, jsonToObjectDeserializer)
        myRetryInterval = props.intProperty("conf.gatekeeper.director.retry", -1)
        if (props.testProperty("conf.gatekeeper.director.auto")) {
            myServer.findService("director-user", DirectorFoundRunnable(), false)
        } else {
            val newDirectorHost = hostDescFromPropertiesFactory.fromProperties("conf.gatekeeper.director")
            if (newDirectorHost == null) {
                gorgel.error("no director specified")
            } else {
                setDirectorHost(newDirectorHost)
            }
        }
        myServer.registerShutdownWatcher(object : ShutdownWatcher {
            override fun noteShutdown() {
                myDirectorActorFactory.disconnectDirector()
            }
        })
    }
}
