package org.elkoserver.server.gatekeeper;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.elkoserver.foundation.boot.Bootable;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.properties.ElkoProperties;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.ServiceFactory;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

/**
 * The Elko boot class for the Gatekeeper.  The Gatekeeper is a server that
 * provides login reservation and authentication services for other Elko
 * servers such as the Director.
 */
public class GatekeeperBoot implements Bootable {
    private TraceFactory traceFactory;
    private Trace tr;
    private Timer timer;
    private Gatekeeper myGatekeeper;

    /** How long user has before being kicked off, in milliseconds. */
    private int myActionTimeout;

    /** Default action timeout, in seconds. */
    private static final int DEFAULT_ACTION_TIMEOUT = 15;

    public void boot(ElkoProperties props, TraceFactory traceFactory, Clock clock) {
        this.traceFactory = traceFactory;
        tr = traceFactory.trace("gate");
        timer = new Timer(traceFactory, clock);
        Server server = new Server(props, "gatekeeper", tr, timer, clock, traceFactory);

        myGatekeeper = new Gatekeeper(server, tr, timer, traceFactory);
        myActionTimeout = 1000 *
            props.intProperty("conf.gatekeeper.actiontimeout",
                              DEFAULT_ACTION_TIMEOUT);

        if (server.startListeners("conf.listen",
                                  new GatekeeperServiceFactory()) == 0) {
            tr.errori("no listeners specified");
        }
    }

    /**
     * Service factory for the Gatekeeper.
     *
     * The Gatekeeper offers two kinds of service connections:
     *
     *    gatekeeper/user     - for clients seeking services
     *    gatekeeper/admin    - for system administrators
     */
    private class GatekeeperServiceFactory implements ServiceFactory {

        public MessageHandlerFactory provideFactory(String label,
                                                    AuthDesc auth,
                                                    Set<String> allow,
                                                    List<String> serviceNames,
                                                    String protocol)
        {
            boolean allowUser = false;
            boolean allowAdmin = false;

            if (allow.contains("any")) {
                allowUser = true;
                allowAdmin = true;
            } 
            if (allow.contains("admin")) {
                allowAdmin = true;
            }
            if (allow.contains("user")) {
                allowUser = true;
            }

            if (allowAdmin) {
                serviceNames.add("gatekeeper-admin");
            }
            if (allowUser) {
                serviceNames.add("gatekeeper-user");
            }
            return new GatekeeperActorFactory(myGatekeeper, auth, allowAdmin,
                                              allowUser, myActionTimeout, tr, timer, traceFactory);
        }
    }
}
