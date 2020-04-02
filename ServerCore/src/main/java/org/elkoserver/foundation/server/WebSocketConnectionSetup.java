package org.elkoserver.foundation.server;

import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NetAddr;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.foundation.properties.ElkoProperties;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

import java.io.IOException;

class WebSocketConnectionSetup extends BaseConnectionSetup {
    private final String socketURI;
    private final NetworkManager myNetworkManager;
    private final MessageHandlerFactory actorFactory;
    private final String serverAddress;

    WebSocketConnectionSetup(String label, String host, AuthDesc auth, boolean secure, ElkoProperties props, String propRoot, NetworkManager networkManager, MessageHandlerFactory actorFactory, Trace trServer, Trace tr, TraceFactory traceFactory) {
        super(label, host, auth, secure, props, propRoot, trServer, tr, traceFactory);

        socketURI = props.getProperty(propRoot + ".sock", "");
        myNetworkManager = networkManager;
        this.actorFactory = actorFactory;
        String socketURI = props.getProperty(propRoot + ".sock", "");
        serverAddress = host + "/" + socketURI;
    }

    @Override
    public String getProtocol() {
        return "ws";
    }

    @Override
    public String getServerAddress() {
        return serverAddress;
    }

    @Override
    NetAddr tryToStartListener() throws IOException {
        return myNetworkManager.listenWebSocket(
                bind,
                actorFactory,
                msgTrace, secure, socketURI);
    }

    @Override
    String getListenAddressDescription() {
        return host + "/" + socketURI;
    }

    @Override
    String getValueToCompareWithBind() {
        return host;
    }
}
