package org.elkoserver.foundation.server.metadata

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
    var retryInterval: Int = 0
        private set

    companion object {
        /** Connection retry interval default, in seconds.  */
        private const val DEFAULT_CONNECT_RETRY_TIMEOUT = 15
    }

    init {
        protocol = if (isSecure) "s$theProtocol" else theProtocol
        this.retryInterval = if (retryInterval == -1) DEFAULT_CONNECT_RETRY_TIMEOUT else retryInterval
    }
}
