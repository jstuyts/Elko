package org.elkoserver.server.gatekeeper;

import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.net.*;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

import java.time.Clock;
import java.util.function.Consumer;

/**
 * Object to manage the connection to the director.  At any given time, there
 * is a most one director connection.  However, what director is connected
 * can change over time.
 */
class DirectorActorFactory implements MessageHandlerFactory {   
    /** Descriptor for the director host. */
    private HostDesc myDirectorHost;

    /** The active director connection, if there is one. */
    private DirectorActor myDirector;

    /** Network manager for making new outbound connections. */
    private NetworkManager myNetworkManager;

    private Timer timer;
    /** Message dispatcher for director connections. */
    private MessageDispatcher myDispatcher;
    private TraceFactory traceFactory;

    /** The gatekeeper itself. */
    private Gatekeeper myGatekeeper;

    /** Trace object for diagnostics. */
    private Trace tr;
    
    /** Object currently attempting to establish a director connection. */
    private ConnectionRetrier myConnectionRetrier;

    /**
     * Constructor.
     *
     * @param networkManager  A network manager for making the outbound
     *    connections required.
     * @param gatekeeper  The gatekeeper.
     * @param appTrace  Trace object for diagnostics.
     */
    DirectorActorFactory(NetworkManager networkManager, Gatekeeper gatekeeper,
                         Trace appTrace, Timer timer, TraceFactory traceFactory, Clock clock)
    {
        myNetworkManager = networkManager;
        this.timer = timer;
        myDispatcher = new MessageDispatcher(null, traceFactory, clock);
        this.traceFactory = traceFactory;
        myDispatcher.addClass(DirectorActor.class);
        myDirector = null;
        myDirectorHost = null;
        myConnectionRetrier = null;
        myGatekeeper = gatekeeper;
        tr = appTrace;
    }

    /**
     * Open connection to the director.
     *
     * @param director  Host and port of director to open a connection to.
     */
    void connectDirector(HostDesc director) {
        if (!director.protocol().equals("tcp")) {
            tr.errorm("unknown director access protocol '" +
                      director.protocol() + "' for access to " +
                      director.hostPort());
        } else {
            if (myConnectionRetrier != null) {
                myConnectionRetrier.giveUp();
            }
            myDirectorHost = director;
            myConnectionRetrier =
                new ConnectionRetrier(director, "director", myNetworkManager,
                                      this, timer, tr, traceFactory);
        }
    }

    /**
     * Close connection to the open director.
     */
    void disconnectDirector() {
        if (myDirector != null) {
            myDirector.close();
        }
    }

    /**
     * Provide a Message handler for a new director connection.
     *
     * @param connection  The Connection object that was just created.
     */
    public MessageHandler provideMessageHandler(Connection connection) {
        return new DirectorActor(connection, myDispatcher, this,
                                 myDirectorHost, traceFactory);
    }

    /**
     * Issue a reservation request to the director.
     *
     * @param protocol  The protocol for the requested reservation.
     * @param context  The requested context.
     * @param actor  The requested actor.
     * @param handler  Object to handle result.
     */
    void requestReservation(String protocol, String context, String actor,
                            Consumer<Object> handler) {
        if (myDirector == null) {
            handler.accept(new ReservationResult(context, actor,
                                                  "no director available"));
        } else {
            myDirector.requestReservation(protocol, context, actor, handler);
        }
    }
    
    /**
     * Get the gatekeeper itself.
     */
    Gatekeeper gatekeeper() {
        return myGatekeeper;
    }

    /**
     * Set the active director connection.
     *
     * @param director  The (new) active director.
     */
    void setDirector(DirectorActor director) {
        if (myDirector != null && director != null) {
            tr.errorm("setting director when director already set");
        }
        myDirector = director;
    }
}
