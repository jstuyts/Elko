package org.elkoserver.foundation.server;

import org.elkoserver.foundation.net.NetAddr;

interface ConnectionSetup {
    String getProtocol();

    String getServerAddress();

    NetAddr startListener();
}
