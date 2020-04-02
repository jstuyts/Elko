package org.elkoserver.foundation.server;

import org.elkoserver.foundation.net.JSONByteIOFramerFactory;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NetAddr;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.foundation.properties.ElkoProperties;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

import java.io.IOException;

class TcpConnectionSetup extends BaseTcpConnectionSetup {
    TcpConnectionSetup(String label, String host, AuthDesc auth, boolean secure, ElkoProperties props, String propRoot, NetworkManager myNetworkManager, MessageHandlerFactory actorFactory, Trace trServer, Trace tr, TraceFactory traceFactory) {
        super(label, host, auth, secure, props, propRoot, myNetworkManager, actorFactory, trServer, tr, traceFactory);
    }

    @Override
    public String getProtocol() {
        return "tcp";
    }

    @Override
    NetAddr createListenAddress() throws IOException {
        return myNetworkManager.listenTCP(
                bind,
                actorFactory,
                msgTrace, secure, new JSONByteIOFramerFactory(msgTrace, traceFactory));
    }
}
