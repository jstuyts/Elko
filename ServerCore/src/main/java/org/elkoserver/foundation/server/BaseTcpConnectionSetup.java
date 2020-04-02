package org.elkoserver.foundation.server;

import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NetAddr;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.foundation.properties.ElkoProperties;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

import java.io.IOException;

abstract class BaseTcpConnectionSetup extends BaseConnectionSetup {
    private String hostIncludingPortNumber;
    final NetworkManager myNetworkManager;
    final MessageHandlerFactory actorFactory;

    BaseTcpConnectionSetup(String label, String host, AuthDesc auth, boolean secure, ElkoProperties props, String propRoot, NetworkManager networkManager, MessageHandlerFactory actorFactory, Trace trServer, Trace tr, TraceFactory traceFactory) {
        super(label, host, auth, secure, props, propRoot, trServer, tr, traceFactory);

        hostIncludingPortNumber = host;
        myNetworkManager = networkManager;
        this.actorFactory = actorFactory;
    }

    @Override
    public String getServerAddress() {
        return hostIncludingPortNumber;
    }

    @Override
    NetAddr tryToStartListener() throws IOException {
        NetAddr result = createListenAddress();
        if (host.indexOf(':') < 0) {
            hostIncludingPortNumber = host + ":" + result.getPort();
        }
        return result;
    }

    abstract NetAddr createListenAddress() throws IOException;

    @Override
    String getListenAddressDescription() {
        return hostIncludingPortNumber;
    }

    @Override
    String getValueToCompareWithBind() {
        return hostIncludingPortNumber;
    }
}
