package org.elkoserver.foundation.server;

import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NetAddr;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

import java.io.IOException;

class RtcpConnectionSetup extends BaseTcpConnectionSetup {
    RtcpConnectionSetup(String label, String host, AuthDesc auth, boolean secure, BootProperties props, String propRoot, NetworkManager myNetworkManager, MessageHandlerFactory actorFactory, Trace trServer, Trace tr) {
        super(label, host, auth, secure, props, propRoot, myNetworkManager, actorFactory, trServer, tr);
    }

    @Override
    public String getProtocol() {
        return "rtcp";
    }

    @Override
    NetAddr createListenAddress() throws IOException {
        return myNetworkManager.listenRTCP(
                bind,
                actorFactory,
                msgTrace,
                secure);
    }
}