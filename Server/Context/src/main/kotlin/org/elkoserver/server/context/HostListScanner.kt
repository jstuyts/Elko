package org.elkoserver.server.context

import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory

internal class HostListScanner(private val hostDescFromPropertiesFactory: HostDescFromPropertiesFactory) {
    /**
     * Scan a collection of host descriptors from the server's configured
     * property info.
     *
     * @param propRoot  Prefix string for props describing the host set of
     * interest
     *
     * @return a list of host descriptors for the configured collection of host
     * information extracted from the properties.
     */
    fun scan(propRoot: String): List<HostDesc> {
        val result = mutableListOf<HostDesc>()

        var index = 0
        var haveAllHostsBeenProcessed = false
        while (!haveAllHostsBeenProcessed) {
            val hostPropRoot = if (index > 0) "$propRoot$index" else propRoot
            val host = hostDescFromPropertiesFactory.fromProperties(hostPropRoot)
            if (host == null) {
                haveAllHostsBeenProcessed = true
            } else {
                result.add(host)
                index += 1
            }
        }

        return  result;
    }
}
