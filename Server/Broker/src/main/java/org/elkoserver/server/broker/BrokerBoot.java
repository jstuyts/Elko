package org.elkoserver.server.broker;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.elkoserver.foundation.boot.Bootable;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.properties.ElkoProperties;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.ServiceFactory;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

/**
 * The Elko boot class for the Broker.  The Broker is a server allows a
 * cluster of Elko servers of various kinds to find out information about each
 * other's available services (and thus establish interconnectivity) without
 * having to be preconfigured.  It also shields the various servers from
 * order-of-startup issues.  Finally, it provides a place to stand for
 * monitoring and administering a Elko server farm as a whole.
 */
@SuppressWarnings("unused")
public class BrokerBoot implements Bootable {
    private TraceFactory traceFactory;
    private Trace tr;
    private Broker myBroker;

    public void boot(ElkoProperties props, TraceFactory traceFactory, Clock clock) {
        this.traceFactory = traceFactory;
        tr = traceFactory.trace("brok");
        Timer timer = new Timer(traceFactory, clock);
        Server server = new Server(props, "broker", tr, timer, clock, traceFactory);

        myBroker = new Broker(server, tr, timer, traceFactory);

        if (server.startListeners("conf.listen",
                                  new BrokerServiceFactory()) == 0) {
            tr.errori("no listeners specified");
        }

        for (ServiceDesc service : server.services()) {
            service.setProviderID(0);
            myBroker.addService(service);
        }
    }

    /**
     * Service factory for the Broker.
     *
     * The Broker offers two kinds of service connections:
     *
     *    broker/client - for servers being brokered or requesting brokerage
     *    broker/admin  - for system administrators
     */
    private class BrokerServiceFactory implements ServiceFactory {

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
                serviceNames.add("broker-admin");
            }
            if (allowClient) {
                serviceNames.add("broker-client");
            }
            
            return new BrokerActorFactory(myBroker, auth, allowAdmin,
                                          allowClient, tr, traceFactory);
        }
    }
}
