package org.elkoserver.foundation.server;

import org.elkoserver.foundation.net.JSONHTTPFramer;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NetAddr;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.foundation.properties.ElkoProperties;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

import java.io.IOException;

class HttpConnectionSetup extends BaseConnectionSetup {
    private final String domain;
    private final NetworkManager myNetworkManager;
    private final MessageHandlerFactory actorFactory;
    private final String rootURI;
    private final String serverAddress;

    HttpConnectionSetup(String label, String host, AuthDesc auth, boolean secure, ElkoProperties props, String propRoot, NetworkManager networkManager, MessageHandlerFactory actorFactory, Trace trServer, Trace tr, TraceFactory traceFactory) {
        super(label, host, auth, secure, props, propRoot, trServer, tr, traceFactory);

        domain = determineDomain(host, props, propRoot);
        myNetworkManager = networkManager;
        this.actorFactory = actorFactory;
        rootURI = props.getProperty(propRoot + ".root", "");
        serverAddress = host + "/" + rootURI;
    }

    private String determineDomain(String host, ElkoProperties props, String propRoot) {
        String result = props.getProperty(propRoot + ".domain");

        if (result == null) {
            int colonIndex = host.indexOf(':');
            if (colonIndex == -1) {
                result = host;
            } else {
                result = host.substring(0, colonIndex);
            }
            int indexOfLastDot = result.lastIndexOf('.');
            indexOfLastDot = result.lastIndexOf('.', indexOfLastDot - 1);
            if (indexOfLastDot > 0) {
                result = result.substring(indexOfLastDot + 1);
            }
        }

        return result;
    }

    @Override
    public String getProtocol() {
        return "http";
    }

    @Override
    public String getServerAddress() {
        return serverAddress;
    }

    @Override
    NetAddr tryToStartListener() throws IOException {
        return myNetworkManager.listenHTTP(
                bind,
                actorFactory,
                msgTrace, secure, rootURI,
                new JSONHTTPFramer(msgTrace, traceFactory));
    }

    @Override
    String getListenAddressDescription() {
        return host + "/" + rootURI + "/ in domain " + domain;
    }

    @Override
    String getValueToCompareWithBind() {
        return host;
    }
}
