package org.elkoserver.foundation.net;

import org.elkoserver.foundation.timer.Timeout;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.util.trace.TraceFactory;

import static java.util.Locale.ENGLISH;

/**
 * Message handler for HTTP requests wrapping a message stream.
 */
class HTTPMessageHandler implements MessageHandler {
    /** The connection this handler handles messages for. */
    private Connection myConnection;

    /** The factory that created this handler, which also contains much of the
        handler implementation logic. */
    private HTTPMessageHandlerFactory myFactory;
    private TraceFactory traceFactory;

    /** Timeout for kicking off users who connect and then don't do anything */
    private Timeout myStartupTimeout;

    /** Flag that startup timeout has tripped, to detect late messages. */
    private boolean myStartupTimeoutTripped;

    /**
     * Constructor.
     *
     * @param connection  The connection this is to be a message handler for.
     * @param factory  The factory what created this.
     * @param startupTimeoutInterval  How long to give new connection to do
     *    something before kicking them off.
     */
    HTTPMessageHandler(Connection connection,
                       HTTPMessageHandlerFactory factory,
                       int startupTimeoutInterval, Timer timer, TraceFactory traceFactory)
    {
        myConnection = connection;
        myFactory = factory;
        this.traceFactory = traceFactory;
        myStartupTimeoutTripped = false;
        /* Kick the user off if they haven't yet done anything. */
        myStartupTimeout =
            timer.after(
                startupTimeoutInterval,
                    () -> {
                        if (myStartupTimeout != null) {
                            myStartupTimeout = null;
                            myStartupTimeoutTripped = true;
                            myConnection.close();
                        }
                    });
    }

    /**
     * Receive notification that the connection has died.
     *
     * In this case, the connection is a TCP connection supporting HTTP, so it
     * doesn't really matter that it died.  Gratuitous TCP connection drops are
     * actually considered normal in the HTTP world.
     *
     * @param connection The (HTTP over TCP) connection that died.
     * @param reason  Why it died.
     */
    public void connectionDied(Connection connection, Throwable reason) {
        myFactory.tcpConnectionDied(connection, reason);
    }

    /**
     * Handle an incoming message from the connection.
     *
     * Since this is an HTTP connection, the message (as parsed by the
     * HTTPRequestFramer) will be an HTTP request.  The nature of the request
     * determines what it is from the perspective of the higher level message
     * stream being supported.
     *
     * A GET for {ROOT}/connect initiates a new application-level session.
     *    This should create a new application-level Connection object, plus
     *    generate a session ID and key for the new session (which are sent
     *    back to the client in the HTTP reply).
     *
     * A GET for {ROOT}/select/{SESSIONID}/{SEQNUM} is a poll for messages from
     *    the server to the client.  {SESSIONID} identifies the session message
     *    stream being polled and also verifies that the requestor is the true
     *    client of the session.  {SEQNUM} ensures that the URI is unique, to
     *    defeat caching by feckless intermediaries.
     *
     * A POST to {ROOT}/xmit/{SESSIONID}/{SEQNUM} is a delivery of messages
     *    from the client to the server.  The interpretation of the URI
     *    components is the same as in the /select/ URI.
     *
     * @param connection  The connection the message was received on.
     * @param rawMessage   The message that was received.  This must be an
     *    instance of HTTPRequest.
     */
    public void processMessage(Connection connection, Object rawMessage) {
        if (myStartupTimeoutTripped) {
            /* They were kicked off for lacktivity, so ignore the message. */
            return;
        }
        if (myStartupTimeout != null) {
            myStartupTimeout.cancel();
            myStartupTimeout = null;
        }

        HTTPRequest message = (HTTPRequest) rawMessage;
        if (traceFactory.comm.getVerbose()) {
            traceFactory.comm.verbosem(connection + " " + message);
        } else if (traceFactory.comm.getDebug()) {
            traceFactory.comm.debugm(connection + " |> " + message.URI());
        }

        String upperCaseMethod = message.method().toUpperCase(ENGLISH);
        switch (upperCaseMethod) {
            case "GET":
                myFactory.handleGET(connection, message.URI(), message.isNonPersistent());
                break;
            case "POST":
                myFactory.handlePOST(connection, message.URI(), message.isNonPersistent(), message.content());
                break;
            case "OPTIONS":
                myFactory.handleOPTIONS(connection, message);
                break;
            default:
                if (traceFactory.comm.getUsage()) {
                    traceFactory.comm.usagem("Received invalid HTTP method " +
                            message.method() + " from " + connection);
                }
                connection.close();
                break;
        }
    }
}
