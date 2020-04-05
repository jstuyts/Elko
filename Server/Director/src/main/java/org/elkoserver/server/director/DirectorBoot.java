package org.elkoserver.server.director;

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
 * The Elko boot class for the Director.  The Director is a server that
 * provides load balancing and context location directory services for Elko
 * applications using the Context Server.
 */
@SuppressWarnings("unused")
public class DirectorBoot implements Bootable {
    private TraceFactory traceFactory;
    private Trace tr;
    private Director myDirector;

    public void boot(ElkoProperties props, TraceFactory traceFactory, Clock clock) {
        this.traceFactory = traceFactory;
        tr = traceFactory.trace("dire");
        Timer timer = new Timer(traceFactory, clock);
        Server server = new Server(props, "director", tr, timer, clock, traceFactory);

        myDirector = new Director(server, tr, traceFactory, clock);

        if (server.startListeners("conf.listen",
                                  new DirectorServiceFactory()) == 0) {
            tr.errori("no listeners specified");
        }
    }

    /**
     * Service factory for the Director.
     *
     * The Director offers three kinds of service connections:
     *
     *    director/provider - for context servers offering services
     *    director/user     - for clients seeking services
     *    director/admin    - for system administrators
     */
    private class DirectorServiceFactory implements ServiceFactory {

        public MessageHandlerFactory provideFactory(String label,
                                                    AuthDesc auth,
                                                    Set<String> allow,
                                                    List<String> serviceNames,
                                                    String protocol)
        {
            boolean allowAdmin = false;
            boolean allowProvider = false;
            boolean allowUser = false;

            if (allow.contains("any")) {
                allowAdmin = true;
                allowProvider = true;
                allowUser = true;
            }

            if (allow.contains("admin")) {
                allowAdmin = true;
            }
            if (allow.contains("provider")) {
                allowProvider = true;
            }
            if (allow.contains("user")) {
                allowUser = true;
            }

            if (allowAdmin) {
                serviceNames.add("director-admin");
            }
            if (allowProvider) {
                serviceNames.add("director-provider");
            }
            if (allowUser) {
                serviceNames.add("director-user");
            }

            return new DirectorActorFactory(myDirector, auth, allowAdmin,
                                            allowProvider, allowUser, tr, traceFactory);
        }
    }
}
