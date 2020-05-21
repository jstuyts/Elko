package org.elkoserver.foundation.server.metadata

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.util.trace.TraceFactory

/**
 * Contact information for establishing a network connection to a host.
 *
 * @param theProtocol  Protocol spoken.
 * @param isSecure  Flag that is true if protocol is secure.
 * @param hostPort  Host/port/path to address for service.
 * @param auth  Authorization.
 * @param retryInterval  Connection retry interval, in seconds, or -1 to
 *    accept the default (currently 15).
 */
class HostDesc(theProtocol: String?, isSecure: Boolean,
               val hostPort: String?,
               val auth: AuthDesc, retryInterval: Int) {
    /** Protocol spoken.  */
    var protocol: String? = null
        private set

    /** Retry interval for reconnect attempts, in seconds (-1 for default).  */
    var retryInterval = 0
        private set

    companion object {
        /** Connection retry interval default, in seconds.  */
        private const val DEFAULT_CONNECT_RETRY_TIMEOUT = 15

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
        @Deprecated(message = "Top-level function which require passing in objects for dependencies. Use an instance of the factory instead.",
                replaceWith = ReplaceWith(
                        imports = ["org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory"],
                        expression = "hostDescFromPropertiesFactory.fromProperties(propRoot)"))
        fun fromProperties(props: ElkoProperties,
                           propRoot: String, traceFactory: TraceFactory): HostDesc? {
            val host = props.getProperty("$propRoot.host")
            return if (host == null) {
                null
            } else {
                val protocol = props.getProperty("$propRoot.protocol", "tcp")
                val auth = AuthDesc.fromProperties(props, propRoot, traceFactory.comm)
                val retry = props.intProperty("$propRoot.retry", -1)
                HostDesc(protocol, false, host, auth, retry)
            }
        }
    }

    init {
        protocol = if (isSecure) "s$theProtocol" else theProtocol
        this.retryInterval = if (retryInterval == -1) DEFAULT_CONNECT_RETRY_TIMEOUT else retryInterval
    }
}
