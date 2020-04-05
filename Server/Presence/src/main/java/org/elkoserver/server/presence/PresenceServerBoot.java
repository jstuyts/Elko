package org.elkoserver.server.presence;

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
 * The Elko boot class for the Presence Server.  This server allows a group of
 * Context Servers to keep track of the online presences of the various other
 * users in their own users' social graphs.
 */
@SuppressWarnings("unused")
public class PresenceServerBoot implements Bootable {
    private TraceFactory traceFactory;
    private Trace tr;
    private PresenceServer myPresenceServer;

    public void boot(ElkoProperties props, TraceFactory traceFactory, Clock clock) {
        this.traceFactory = traceFactory;
        tr = traceFactory.trace("pres");
        Timer timer = new Timer(traceFactory, clock);
        Server server = new Server(props, "presence", tr, timer, clock, traceFactory);

        myPresenceServer = new PresenceServer(server, tr, traceFactory, clock);

        if (server.startListeners("conf.listen",
                                  new PresenceServiceFactory()) == 0) {
            tr.errori("no listeners specified");
        }
    }

    /**
     * Service factory for the Presence Server.
     *
     * The Presence Server offers two kinds of service connections:
     *
     *    presence/client - for context servers monitoring presence information
     *    presence/admin  - for system administrators
     */
    private class PresenceServiceFactory implements ServiceFactory {

        public MessageHandlerFactory provideFactory(String label,
                                                    AuthDesc auth,
                                                    Set<String> allow,
                                                    List<String> serviceNames,
                                                    String protocol)
        {
            boolean allowClient = false;
            boolean allowAdmin = false;

            if (allow.contains("any")) {
                allowClient = true;
                allowAdmin = true;
            } 
            if (allow.contains("admin")) {
                allowAdmin = true;
            }
            if (allow.contains("client")) {
                allowClient = true;
            }

            if (allowAdmin) {
                serviceNames.add("presence-admin");
            }
            if (allowClient) {
                serviceNames.add("presence-client");
            }
            
            return new PresenceActorFactory(myPresenceServer, auth, allowAdmin,
                                            allowClient, tr, traceFactory);
        }
    }
}
