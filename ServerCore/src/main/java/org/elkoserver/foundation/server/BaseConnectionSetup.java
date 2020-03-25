package org.elkoserver.foundation.server;

import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.foundation.net.NetAddr;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

import java.io.IOException;

abstract class BaseConnectionSetup implements ConnectionSetup {
    final String host;
    private final AuthDesc auth;
    final boolean secure;
    final String propRoot;
    private final Trace trServer;
    private final Trace tr;
    final String bind;
    final Trace msgTrace;

    BaseConnectionSetup(String label, String host, AuthDesc auth, boolean secure, BootProperties props, String propRoot, Trace trServer, Trace tr) {
        this.host = host;
        this.auth = auth;
        this.secure = secure;
        this.propRoot = propRoot;
        this.trServer = trServer;
        this.tr = tr;

        bind = props.getProperty(propRoot + ".bind", host);

        boolean dontLog = props.testProperty(propRoot + ".dontlog");
        if (dontLog) {
            msgTrace = Trace.none;
        } else if (label != null) {
            msgTrace = Trace.comm.subTrace(label);
        } else {
            msgTrace = Trace.comm.subTrace("cli");
        }
    }

    @Override
    public NetAddr startListener() {
        NetAddr result;

        try {
            result = tryToStartListener();

            trServer.noticei("listening for " + getProtocol() +
                    " connections" + getConnectionsSuffixForNotice() + " on " +
                    getListenAddressDescription() +
                    (!bind.equals(getValueToCompareWithBind()) ? (" (" + bind + ")") : "") +
                    " (" + auth.mode() + ")" +
                    (secure ? " (secure mode)" : ""));
        } catch (IOException e) {
            tr.errorm("unable to open " + getProtocol() + getProtocolSuffixForErrorMessage() +
                    " listener " + propRoot +
                    " on requested host: " + e);
            throw new IllegalStateException();
        }

        return result;
    }

    abstract NetAddr tryToStartListener() throws IOException;

    abstract String getListenAddressDescription();

    abstract String getValueToCompareWithBind();

    String getConnectionsSuffixForNotice() {
        return "";
    }

    String getProtocolSuffixForErrorMessage() {
        return "";
    }
}
