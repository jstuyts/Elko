package org.elkoserver.foundation.server;

import org.elkoserver.foundation.actor.NonRoutingActor;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.util.trace.TraceFactory;

import java.util.Collections;
import java.util.List;

import static org.elkoserver.json.JSONLiteralFactory.targetVerb;

/**
 * Actor representing a server's connection to its farm's broker.
 */
class BrokerActor extends NonRoutingActor
{
    /** The local server. */
    private Server myServer;

    /** Load watcher for this actor to report load to the broker. */
    private LoadWatcher myLoadWatcher;

    /**
     * Constructor.
     *
     * @param connection  The connection for communicating with the broker.
     * @param dispatcher  Dispatcher for routing messages from the broker.
     * @param server  This actor's own server.
     * @param host  The broker's host address.
     */
    BrokerActor(Connection connection, MessageDispatcher dispatcher,
                Server server, HostDesc host, TraceFactory traceFactory)
    {
        super(connection, dispatcher, traceFactory);
        myServer = server;
        send(msgAuth(this, host.auth(), myServer.serverName()));
        send(msgWillserve(this, myServer.services()));
        myLoadWatcher = loadFactor -> send(msgLoad(BrokerActor.this, loadFactor));
        myServer.registerLoadWatcher(myLoadWatcher);
        myServer.brokerConnected(this);
    }

    /**
     * Handle loss of connection from the broker.
     *
     * @param connection  The broker connection that died.
     * @param reason  Exception explaining why.
     */
    public void connectionDied(Connection connection, Throwable reason) {
        traceFactory.comm.eventm("lost broker connection " + connection + ": " +
                          reason);
        myServer.unregisterLoadWatcher(myLoadWatcher);
        myServer.brokerConnected(null);
    }

    /**
     * Send a request for a service description to the broker.
     *
     * @param service  The service being requested.
     * @param monitor  Should broker continue watching for more results?
     * @param tag  Optional tag to match response with the request.
     */
    void findService(String service, boolean monitor, String tag) {
        send(msgFind(this, service, monitor, tag));
    }

    /**
     * Get this object's reference string.  This singleton object's reference
     * string is always 'broker'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "broker";
    }

    /**
     * Register an individual service with this broker.
     *
     * @param service  The service to register.
     */
    void registerService(ServiceDesc service) {
        send(msgWillserve(this, Collections.singletonList(service)));
    }

    /* ----- JSON method protocol ------------------------------------------ */

    /**
     * Handle a 'find' message: process the result of a previously sent 'find'
     * request.
     *
     * @param desc  The service description(s) returned by the broker.
     * @param tag  The 'tag' string from the request.  (Optional, default "").
     */
    @JSONMethod({ "desc", "tag" })
    public void find(BrokerActor from, ServiceDesc[] desc, OptString tag) {
        myServer.foundService(desc, tag.value(""));
    }

    /**
     * Handle a 'reinit' message: reinitialize this server.
     */
    @JSONMethod
    public void reinit(BrokerActor from) {
        myServer.reinit();
    }

    /**
     * Handle a 'shutdown' message: shut down this server.
     *
     * @param kill  Terminate abruptly?  (Optional, default false).
     */
    @JSONMethod({ "kill" })
    public void shutdown(BrokerActor from, OptBoolean kill) {
        myServer.shutdown(kill.value(false));
    }

    /* ----- JSON message generators --------------------------------------- */

    /**
     * Create a 'find' message: ask the broker to look up service information.
     *  @param target  Object the message is being sent to.
     * @param service  The service being requested.
     * @param monitor  If true, broker should keep watching for additional
 *    matches for the requested service.
     * @param tag  Optional tag to match response with the request.
     */
    private static JSONLiteral msgFind(Referenceable target, String service,
                                       boolean monitor, String tag)
    {
        JSONLiteral msg = targetVerb(target, "find");
        msg.addParameter("service", service);
        msg.addParameter("wait", -1);
        if (monitor) {
            msg.addParameter("monitor", true);
        }
        msg.addParameterOpt("tag", tag);
        msg.finish();
        return msg;
    }

    /**
     * Create a 'load' message: report this server's load to the broker.
     *
     * @param target  Object the message is being sent to.
     * @param factor  Load factor to report.
     */
    private static JSONLiteral msgLoad(Referenceable target, double factor) {
        JSONLiteral msg = targetVerb(target, "load");
        msg.addParameter("factor", factor);
        msg.finish();
        return msg;
    }

    /**
     * Create a 'willserve' message: notify the broker that this server is
     * offering one or more services.
     *
     * @param target  Object the message is being sent to.
     * @param services  List of the services being offered.
     */
    private static JSONLiteral msgWillserve(Referenceable target,
                                            List<ServiceDesc> services)
    {
        JSONLiteral msg = targetVerb(target, "willserve");
        msg.addParameter("services", ServiceDesc.encodeArray(services));
        msg.finish();
        return msg;
    }
}
