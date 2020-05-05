package org.elkoserver.server.context

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.util.trace.TraceFactory
import java.util.LinkedList

/**
 * Scan a collection of host descriptors from the server's configured
 * property info.
 *
 * @param props  Properties, from the command line and elsewhere.
 * @param propRoot  Prefix string for props describing the host set of
 * interest
 *
 * @return a list of host descriptors for the configured collection of host
 * information extracted from the properties.
 */
internal fun scanHostList(props: ElkoProperties, propRoot: String, traceFactory: TraceFactory): MutableList<HostDesc> {
    var index = 0
    val hosts: MutableList<HostDesc> = LinkedList()
    while (true) {
        var hostPropRoot = propRoot
        if (index > 0) {
            hostPropRoot += index
        }
        ++index
        val host = HostDesc.fromProperties(props, hostPropRoot, traceFactory) ?: return hosts
        hosts.add(host)
    }
}