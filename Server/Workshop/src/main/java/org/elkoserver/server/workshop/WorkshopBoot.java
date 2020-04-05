package org.elkoserver.server.workshop;

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
 * The boot class for the Workshop.  The Workshop is a server that provides a
 * place for arbitrary, configurable worker objects to run.
 */
@SuppressWarnings("unused")
public class WorkshopBoot implements Bootable {
    private Trace tr;
    private Workshop myWorkshop;
    private TraceFactory traceFactory;

    public void boot(ElkoProperties props, TraceFactory traceFactory, Clock clock) {
        this.traceFactory = traceFactory;
        tr = traceFactory.trace("work");
        Timer timer = new Timer(traceFactory, clock);
        Server server = new Server(props, "workshop", tr, timer, clock, traceFactory);

        myWorkshop = new Workshop(server, tr, traceFactory, clock);

        if (server.startListeners("conf.listen",
                                  new WorkshopServiceFactory()) == 0) {
            tr.errori("no listeners specified");
        } else {
            myWorkshop.loadStartupWorkers();
        }
    }

    /**
     * Service factory for the Workshop.
     *
     * The Workshop offers two kinds of service connections:
     *
     *    workshop-service - for messages to objects offering services
     *    workshop-admin - for system administrators
     */
    private class WorkshopServiceFactory implements ServiceFactory {

        public MessageHandlerFactory provideFactory(String label,
                                                    AuthDesc auth,
                                                    Set<String> allow,
                                                    List<String> serviceNames,
                                                    String protocol)
        {
            boolean allowAdmin = false;
            boolean allowClient = false;

            if (allow.contains("any")) {
                allowAdmin = true;
                allowClient = true;
            }
            if (allow.contains("admin")) {
                allowAdmin = true;
            }
            if (allow.contains("workshop")) {
                allowClient = true;
            }

            if (allowAdmin) {
                serviceNames.add("workshop-admin");
            }
            if (allowClient) {
                serviceNames.add("workshop-service");
            }
            
            return new WorkshopActorFactory(myWorkshop, auth, allowAdmin,
                                            allowClient, tr, traceFactory);
        }
    }
}
