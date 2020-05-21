package org.elkoserver.foundation.net

import java.net.InetAddress

/**
 * An IP network address and port number combination, represented in a somewhat
 * friendlier way than [InetSocketAddress][java.net.InetSocketAddress]
 * does.
 */
class NetAddr {
    /** The address.  A null means "all local IP addresses".  */
    internal val inetAddress: InetAddress?

    /**
     * Get the port number.
     *
     * @return the port number embodied by this object.
     */
    /** The port number.  */
    val port: Int

    /**
     * Construct a NetAddr from a string in the form:
     * `*hostName*:*portNumber*` or `*hostName*`.
     * If the `:*portNumber*` is omitted, port number 0 will be
     * assumed.  The host name may be either a DNS name or a raw IPv4 address
     * in dotted decimal format.  Alternatively, it may be both these,
     * separated by a slash, in which case only the part after the slash is
     * significant.  If the significant part of the hostname is absent, then
     * the port is associated with all local IP addresses.
     *
     * @param addressStr  The network address string, as described above.
     */
    constructor(addressStr: String?) {
        var actualAddressStr = addressStr
        if (actualAddressStr == null) {
            actualAddressStr = ""
        }
        val colon = actualAddressStr.indexOf(':')
        if (colon < 0) {
            port = 0
        } else {
            port = actualAddressStr.substring(colon + 1).toInt()
            actualAddressStr = actualAddressStr.take(colon)
        }
        val slash = actualAddressStr.indexOf('/')
        if (slash >= 0) {
            actualAddressStr = actualAddressStr.substring(slash + 1)
        }
        inetAddress = if (actualAddressStr.isNotEmpty()) {
            InetAddress.getByName(actualAddressStr)
        } else {
            null
        }
    }

    /**
     * Construct a new NetAddr given an IP address and a port number.
     *
     * @param inetAddress  An IP address, where null =&gt; all local IP addresses.
     * @param portNumber  A port at that IP address.
     */
    constructor(inetAddress: InetAddress, portNumber: Int) {
        this.inetAddress = inetAddress
        port = portNumber
    }

    /**
     * Test if another object is a NetAddr denoting the same address as this.
     *
     * @param other  The other object to test for equality.
     * @return true if this and 'other' denote the same net address.
     */
    override fun equals(other: Any?): Boolean {
        if (other !is NetAddr) {
            return false
        }
        if (port != other.port) {
            return false
        }
        val otherInetAddress = other.inetAddress
        if (inetAddress === otherInetAddress) {
            return true
        }
        return if (inetAddress == null || otherInetAddress == null) {
            false
        } else inetAddress == otherInetAddress
    }

    /**
     * Get a hash code for this address.
     *
     * @return a hash code that accounts for both the IP address and port.
     */
    override fun hashCode(): Int {
        return if (inetAddress == null) {
            port
        } else {
            inetAddress.hashCode() xor port
        }
    }

    /**
     * Produce a printable representation of this.
     *
     * @return a nicely formatted string representing this address.
     */
    override fun toString(): String {
        return if (inetAddress == null) {
            ":$port"
        } else {
            "${inetAddress.hostAddress}:$port"
        }
    }
}
