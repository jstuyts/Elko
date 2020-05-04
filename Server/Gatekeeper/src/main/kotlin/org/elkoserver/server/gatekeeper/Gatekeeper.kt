package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.actor.BasicProtocolActor
import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.objdb.ObjDB
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.lang.reflect.InvocationTargetException
import java.time.Clock
import java.util.function.Consumer

/**
 * The Gatekeeper itself as presented to its configured [Authorizer]
 * object.  This is the [Authorizer]'s linkage back to the systems that
 * do most of the real work in the Gatekeeper.
 */
class Gatekeeper internal constructor(
        private val myServer: Server,
        private val tr: Trace,
        timer: Timer,
        traceFactory: TraceFactory,
        clock: Clock) {
    /** Table for mapping object references in messages.  */
    private val myRefTable: RefTable

    /** Local auth service module.  */
    private val myAuthorizer: Authorizer

    /** Host description for the director.  */
    private var myDirectorHost: HostDesc?

    /** Retry interval for connection to director, in seconds, or -1 to take
     * the default.  */
    private val myRetryInterval: Int

    /** Object for managing director connections.  */
    private val myDirectorActorFactory: DirectorActorFactory

    private inner class DirectorFoundRunnable : Consumer<Any> {
        override fun accept(obj: Any) {
            val desc = obj as Array<ServiceDesc>
            if (desc[0].failure() != null) {
                tr.errorm("unable to find director: " + desc[0].failure())
            } else {
                setDirectorHost(desc[0].asHostDesc(myRetryInterval))
            }
        }
    }

    /**
     * Get the auth service currently in use.
     *
     * @return the auth service object for this server.
     */
    fun authorizer(): Authorizer? {
        return myAuthorizer
    }

    /**
     * Get the current director host.
     *
     * @return a host descriptor describing the current director connection.
     */
    fun directorHost(): HostDesc? {
        return myDirectorHost
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
            throw MessageHandlerException("actor " + from +
                    " attempted admin operation without authorization")
        }
    }

    /**
     * Open an asynchronous database.  The location of the database (directory
     * path or remote repository host) is specified by properties.
     *
     * @param propRoot  Prefix string for all the properties describing the
     * database that is to be opened.
     *
     * @return an object for communicating with the open database, or
     * null if the database location was not properly specified.
     */
    fun openObjectDatabase(propRoot: String?): ObjDB {
        return myServer.openObjectDatabase(propRoot)
    }

    /**
     * Get the server's configuration properties.
     *
     * @return the configuration properties table for this server invocation.
     */
    fun properties(): ElkoProperties {
        return myServer.props()
    }

    /**
     * Get the object reference table for this gatekeeper.
     *
     * @return the object reference table.
     */
    fun refTable(): RefTable {
        return myRefTable
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
    fun requestReservation(protocol: String, context: String, actor: String, handler: Consumer<Any?>) {
        if (myDirectorHost == null) {
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
    fun serverName(): String {
        return myServer.serverName()
    }

    /**
     * Change the director to which this gatekeeper is connected.  This
     * includes disconnecting from the current director (if one is connected)
     * and connecting to the new one.
     *
     * @param host  The new director host.
     */
    fun setDirectorHost(host: HostDesc) {
        if (myDirectorHost != null) {
            myDirectorActorFactory.disconnectDirector()
        }
        myDirectorHost = host
        myDirectorActorFactory.connectDirector(host)
    }

    /**
     * Shutdown the server.
     *
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    fun shutdown(kill: Boolean) {
        myServer.shutdown(kill)
    }

    /**
     * Get the trace object.  This is used for writing messages to the server
     * log.
     *
     * @return the trace object for this server.
     */
    fun trace(): Trace {
        return tr
    }

    init {
        myRefTable = RefTable(null, traceFactory, clock)
        myRefTable.addRef(UserHandler(this, traceFactory))
        myRefTable.addRef(AdminHandler(this, traceFactory))
        val props = myServer.props()
        myDirectorActorFactory = DirectorActorFactory(myServer.networkManager(), this, tr, timer, traceFactory, clock)
        myRetryInterval = props.intProperty("conf.gatekeeper.director.retry", -1)
        myDirectorHost = null
        if (props.testProperty("conf.gatekeeper.director.auto")) {
            myServer.findService("director-user", DirectorFoundRunnable(), false)
        } else {
            val directorHost = HostDesc.fromProperties(props, "conf.gatekeeper.director", traceFactory)
            if (directorHost == null) {
                tr.errori("no director specified")
            } else {
                setDirectorHost(directorHost)
            }
        }
        val authorizerClassName = props.getProperty("conf.gatekeeper.authorizer",
                "org.elkoserver.server.gatekeeper.passwd.PasswdAuthorizer")
        val authorizerClass: Class<*>
        authorizerClass = try {
            Class.forName(authorizerClassName)
        } catch (e: ClassNotFoundException) {
            tr.fatalError("auth service class " + authorizerClassName +
                    " not found")
        }
        myAuthorizer = try {
            authorizerClass.getConstructor(TraceFactory::class.java).newInstance(traceFactory) as Authorizer
        } catch (e: IllegalAccessException) {
            tr.fatalError("unable to access auth service constructor: $e")
        } catch (e: InstantiationException) {
            tr.fatalError("unable to instantiate auth service object: $e")
        } catch (e: NoSuchMethodException) {
            tr.fatalError("auth service object does not have a public constructor accepting a trace factory: $e")
        } catch (e: InvocationTargetException) {
            tr.fatalError("error occurred during instantiation of auth service object: $e")
        }
        myAuthorizer.initialize(this)
        myServer.registerShutdownWatcher {
            myDirectorActorFactory.disconnectDirector()
            myAuthorizer.shutdown()
        }
    }
}