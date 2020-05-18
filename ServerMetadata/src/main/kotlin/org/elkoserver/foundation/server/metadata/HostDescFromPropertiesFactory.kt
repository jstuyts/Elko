package org.elkoserver.foundation.server.metadata

import org.elkoserver.foundation.properties.ElkoProperties

class HostDescFromPropertiesFactory(private val props: ElkoProperties, private val authDescFromPropertiesFactory: AuthDescFromPropertiesFactory) {
    /**
     * Create a HostDesc object from specifications provided by properties:
     *
     * `"*propRoot*.host"` should contain a host:port
     * string.<br></br>
     * `"*propRoot*.protocol"`, if given, should specify a protocol
     * name.  If not given, the protocol defaults to "tcp".<br></br>
     * `"*propRoot*.retry"`, an integer, if given, is the retry
     * interval, in seconds.
     *
     * @param props  Properties to examine for a host description.
     * @param propRoot  Root property name.
     *
     * @return a new HostDesc object as specified by 'props', or null if no such
     * host was described.
     */
    fun fromProperties(propRoot: String) =
            props.getProperty("$propRoot.host")?.let { host ->
                val protocol = props.getProperty("$propRoot.protocol", "tcp")
                val auth = authDescFromPropertiesFactory.fromProperties(propRoot)
                val retry = props.intProperty("$propRoot.retry", -1)
                HostDesc(protocol, false, host, auth, retry)
            }
}
