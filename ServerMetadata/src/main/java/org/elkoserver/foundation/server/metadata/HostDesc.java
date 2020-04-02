package org.elkoserver.foundation.server.metadata;

import org.elkoserver.foundation.properties.ElkoProperties;
import org.elkoserver.util.trace.TraceFactory;

/**
 * Contact information for establishing a network connection to a host.
 */
public class HostDesc {
    /** Protocol spoken. */
    private String myProtocol;

    /** Where to direct clients to. */
    private String myHostPort;

    /** Authorization for connecting. */
    private AuthDesc myAuth;

    /** Retry interval for reconnect attempts, in seconds (-1 for default). */
    private int myRetryInterval;

    /** Connection retry interval default, in seconds. */
    private static final int DEFAULT_CONNECT_RETRY_TIMEOUT = 15;

    /**
     * Constructor.
     *
     * @param protocol  Protocol spoken.
     * @param isSecure  Flag that is true if protocol is secure.
     * @param hostPort  Host/port/path to address for service.
     * @param auth  Authorization.
     * @param retryInterval  Connection retry interval, in seconds, or -1 to
     *    accept the default (currently 15).
     */
    public HostDesc(String protocol, boolean isSecure, String hostPort,
                    AuthDesc auth, int retryInterval)
    {
        if (isSecure) {
            myProtocol = "s" + protocol;
        } else {
            myProtocol = protocol;
        }
        myHostPort = hostPort;
        myAuth = auth;
        if (retryInterval == -1) {
            myRetryInterval = DEFAULT_CONNECT_RETRY_TIMEOUT;
        } else {
            myRetryInterval = retryInterval;
        }
    }

    /**
     * Constructor, taking most defaults.
     *
     * <p>Equivalent to <tt>new HostDesc(protocol, false, hostPort, null,
     * -1, false)</tt>
     *
     * @param protocol  Protocol spoken.
     * @param hostPort  Host/port/path to address for service.
     */
    public HostDesc(String protocol, String hostPort) {
        this(protocol, false, hostPort, null, -1);
    }

    /**
     * Create a HostDesc object from specifications provided by properties:
     *
     * <p><tt>"<i>propRoot</i>.host"</tt> should contain a host:port
     *    string.<br>
     * <tt>"<i>propRoot</i>.protocol"</tt>, if given, should specify a protocol
     *    name.  If not given, the protocol defaults to "tcp".<br>
     * <tt>"<i>propRoot</i>.retry"</tt>, an integer, if given, is the retry
     *    interval, in seconds.
     *
     * @param props  Properties to examine for a host description.
     * @param propRoot  Root property name.
     *
     * @return a new HostDesc object as specified by 'props', or null if no such
     *    host was described.
     */
    public static HostDesc fromProperties(ElkoProperties props,
                                          String propRoot, TraceFactory traceFactory)
    {
        String host = props.getProperty(propRoot + ".host");
        if (host == null) {
            return null;
        } else {
            String protocol = props.getProperty(propRoot + ".protocol", "tcp");
            AuthDesc auth =
                AuthDesc.fromProperties(props, propRoot, traceFactory.comm);
            if (auth == null) {
                return null;
            }
            int retry = props.intProperty(propRoot + ".retry", -1);

            return new HostDesc(protocol, false, host, auth, retry);
        }
    }

    /**
     * Get this host's authorization information.
     *
     * @return this host's authorization information, or null if there isn't
     *    any (equivalent to open access).
     */
    public AuthDesc auth() {
        return myAuth;
    }

    /**
     * Get this host's contact address.
     *
     * @return this host's contact address.
     */
    public String hostPort() {
        return myHostPort;
    }

    /**
     * Get this host's protocol.
     *
     * @return this host's protocol.
     */
    public String protocol() {
        return myProtocol;
    }

    /**
     * Get this host's retry interval.
     *
     * @return this host's retry interval, in seconds.
     */
    public int retryInterval() {
        return myRetryInterval;
    }
}
