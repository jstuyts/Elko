package org.elkoserver.server.repository;

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
 * The Elko boot class for the Repository.  The Repository is a server that
 * provides access to persistent storage for objects.
 */
public class RepositoryBoot implements Bootable {
    private TraceFactory traceFactory;
    private Trace tr;
    private Repository myRepository;

    public void boot(ElkoProperties props, TraceFactory traceFactory, Clock clock) {
        this.traceFactory = traceFactory;
        tr = traceFactory.trace("repo");
        Timer timer = new Timer(traceFactory, clock);
        Server server = new Server(props, "rep", tr, timer, clock, traceFactory);

        myRepository = new Repository(server, tr, traceFactory);

        if (server.startListeners("conf.listen",
                                  new RepositoryServiceFactory()) == 0) {
            tr.errori("no listeners specified");
        }
    }

    /**
     * Service factor for the Repository.
     *
     * The Repository offers two kinds of service connections:
     *
     *    repository/rep   - for object storage and retrieval
     *    repository/admin - for system administrators
     */
    private class RepositoryServiceFactory implements ServiceFactory {

        public MessageHandlerFactory provideFactory(String label,
                                                    AuthDesc auth,
                                                    Set<String> allow,
                                                    List<String> serviceNames,
                                                    String protocol)
        {
            boolean allowAdmin = false;
            boolean allowRep = false;

            if (allow.contains("any")) {
                allowAdmin = true;
                allowRep = true;
            }
            if (allow.contains("admin")) {
                allowAdmin = true;
            }
            if (allow.contains("rep")) {
                allowRep = true;
            }

            if (allowAdmin) {
                serviceNames.add("repository-admin");
            }
            if (allowRep) {
                serviceNames.add("repository-rep");
            }
            
            return new RepositoryActorFactory(myRepository, auth, allowAdmin,
                                              allowRep, tr, traceFactory);
        }
    }
}
