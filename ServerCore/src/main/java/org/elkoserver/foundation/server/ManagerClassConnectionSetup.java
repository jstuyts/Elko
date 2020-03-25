package org.elkoserver.foundation.server;

import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NetAddr;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

import java.io.IOException;

class ManagerClassConnectionSetup extends BaseConnectionSetup {
    private String hostIncludingPortNumber;
    private final String mgrClass;
    private final NetworkManager myNetworkManager;
    private final MessageHandlerFactory actorFactory;

    ManagerClassConnectionSetup(String label, String mgrClass, String host, AuthDesc auth, boolean secure, BootProperties props, String propRoot, NetworkManager myNetworkManager, MessageHandlerFactory actorFactory, Trace trServer, Trace tr) {
        super(label, host, auth, secure, props, propRoot, trServer, tr);

        this.mgrClass = mgrClass;
        hostIncludingPortNumber = host;
        this.myNetworkManager = myNetworkManager;
        this.actorFactory = actorFactory;
    }

    @Override
    public String getProtocol() {
        return "manager class: " + mgrClass;
    }

    @Override
    public String getServerAddress() {
        return hostIncludingPortNumber;
    }

    @Override
    NetAddr tryToStartListener() throws IOException {
        NetAddr result = myNetworkManager.listenVia(
                mgrClass,
                propRoot,
                bind,
                actorFactory,
                msgTrace,
                secure);
        if (host.indexOf(':') < 0) {
            hostIncludingPortNumber = host + ":" + result.getPort();
        }
        return result;
    }

    @Override
    String getListenAddressDescription() {
        return hostIncludingPortNumber;
    }

    @Override
    String getValueToCompareWithBind() {
        return hostIncludingPortNumber;
    }

    @Override
    String getConnectionsSuffixForNotice() {
        return " using " + mgrClass;
    }

    @Override
    String getProtocolSuffixForErrorMessage() {
        return " (" + mgrClass + ")";
    }
}
