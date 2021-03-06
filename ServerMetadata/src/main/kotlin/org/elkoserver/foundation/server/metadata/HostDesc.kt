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
    val protocol: String? = if (isSecure) "s$theProtocol" else theProtocol

    /** Retry interval for reconnect attempts, in seconds (-1 for default).  */
    val retryInterval = if (retryInterval == -1) DEFAULT_CONNECT_RETRY_TIMEOUT else retryInterval

    companion object {
        /** Connection retry interval default, in seconds.  */
        private const val DEFAULT_CONNECT_RETRY_TIMEOUT = 15
    }
}
