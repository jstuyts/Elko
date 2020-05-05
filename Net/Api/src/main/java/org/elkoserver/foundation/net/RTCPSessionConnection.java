package org.elkoserver.foundation.net;

import org.elkoserver.foundation.timer.Clock;
import org.elkoserver.foundation.timer.Timeout;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JsonObject;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

import java.security.SecureRandom;
import java.util.LinkedList;

/**
 * An implementation of {@link Connection} that virtualizes a continuous
 * message session out of a series of potentially transient TCP connections.
 */
public class RTCPSessionConnection extends ConnectionBase
{
    /** Trace object for logging message traffic. */
    private final Trace trMsg;

    /** Sequence number of last client->server message received here. */
    private int myClientSendSeqNum;

    /** Sequence number of last server->client message sent from here. */
    private int myServerSendSeqNum;

    /** Queue of outgoing messages not yet ack'd by the client. */
    private final LinkedList<RTCPMessage> myQueue;

    /** Current volume of unacknowledged messages in the outgoing queue. */
    private int myQueueBacklog;

    /** Flag indicating that connection is in the midst of shutting down. */
    private boolean amClosing;

    /** TCP connection for transmitting messages to the client, if a connection
        is currently open, or null if not. */
    private Connection myLiveConnection;

    /** The factory that created this session. */
    private final RTCPMessageHandlerFactory mySessionFactory;
    private final Timer timer;
    private final TraceFactory traceFactory;

    /** Clock: ticks watch for inactive (and thus presumed dead) session. */
    private final Clock myInactivityClock;

    /** Timeout for killing an abandoned session. */
    private Timeout myDisconnectedTimeout;

    /** Last time that there was any traffic on this connection from the user,
        to enable detection of inactive sessions. */
    private long myLastActivityTime;

    /** Time a session may sit idle before killing it, in milliseconds. */
    private int myInactivityTimeoutInterval;

    /** Time a session may sit disconnected before killing it, milliseconds. */
    private int myDisconnectedTimeoutInterval;

    /** Session ID -- a swiss number to authenticate client RTCP requests. */
    private final String mySessionID;

    /** Random number generator, for creating session IDs. */
    @Deprecated // Global variable
    private static final SecureRandom theRandom = new SecureRandom();


    /**
     * Make a new RTCP session connection object for an incoming connection,
     * with a new, internally generated, session ID.
     *
     * @param sessionFactory  Factory for creating RTCP message handler objects
     */
    RTCPSessionConnection(RTCPMessageHandlerFactory sessionFactory, Timer timer, java.time.Clock clock, TraceFactory traceFactory) {
        this(sessionFactory, Math.abs(theRandom.nextLong()), timer, clock, traceFactory);
    }

    /**
     * Make a new RTCP session connection object for an incoming connection,
     * with a given session ID.
     *
     * @param sessionFactory  Factory for creating RTCP message handler objects
     * @param sessionID  The session ID for the session.
     */
    private RTCPSessionConnection(RTCPMessageHandlerFactory sessionFactory,
                                  long sessionID, Timer timer, java.time.Clock clock, TraceFactory traceFactory)
    {
        super(sessionFactory.networkManager(), clock, traceFactory);
        mySessionFactory = sessionFactory;
        this.timer = timer;
        this.traceFactory = traceFactory;
        trMsg = mySessionFactory.msgTrace();
        mySessionID = "" + sessionID;
        mySessionFactory.addSession(this);
        myLastActivityTime = clock.millis();
        if (traceFactory.comm.getEvent()) {
            traceFactory.comm.eventi(this + " new connection session " + mySessionID);
        }
        myServerSendSeqNum = 0;
        myClientSendSeqNum = 0;
        myQueue = new LinkedList<>();
        myQueueBacklog = 0;
        amClosing = false;

        myDisconnectedTimeout = null;

        myDisconnectedTimeoutInterval =
            mySessionFactory.sessionDisconnectedTimeout(false);
        myInactivityTimeoutInterval =
            mySessionFactory.sessionInactivityTimeout(false);
        myInactivityClock =
            timer.every(myInactivityTimeoutInterval/2 + 1000, ignored -> noticeInactivityTick());
        myInactivityClock.start();

        enqueueHandlerFactory(mySessionFactory.innerFactory());
    }

    /**
     * Associate a TCP connection with this session.
     *
     * @param connection  The TCP connection used.
     */
    void acquireTCPConnection(Connection connection) {
        if (myLiveConnection != null) {
            myLiveConnection.close();
        }
        myLiveConnection = connection;
        if (myDisconnectedTimeout != null) {
            myDisconnectedTimeout.cancel();
            myDisconnectedTimeout = null;
        }
        if (traceFactory.comm.getDebug()) {
            traceFactory.comm.debugm("acquire " + connection + " for " + this);
        }
    }

    /**
     * Accept an 'ack' message from the client, acknowledging receipt of one
     * or more messages, and providing activity to keep the session alive.
     *
     * @param clientRecvSeqNum  The message number being acknowledged.
     */
    void clientAck(int clientRecvSeqNum) {
        long timeInactive = clock.millis() - myLastActivityTime;
        noteClientActivity();
        if (traceFactory.comm.getDebug()) {
            traceFactory.comm.debugm(this + " ack " + clientRecvSeqNum);
        }
        discardAcknowledgedMessages(clientRecvSeqNum);
        if (timeInactive > myInactivityTimeoutInterval/4) {
            String ack = mySessionFactory.makeAck(myClientSendSeqNum);
            sendMsg(ack);
        }
    }

    /**
     * Obtain this connection's client send sequence number, the sequence
     * number of the most recent client to server message received by the
     * server.
     *
     * @return this connection's client send sequence number.
     */
    int clientSendSeqNum() {
        return myClientSendSeqNum;
    }

    /**
     * Shut down the connection.
     */
    public void close() {
        if (!amClosing) {
            amClosing = true;

            mySessionFactory.removeSession(this);
            myInactivityClock.stop();
            if (myLiveConnection != null) {
                myLiveConnection.close();
            }
            connectionDied(
                new ConnectionCloseException("Normal RTCP session close"));
        }
    }

    /**
     * Once a message from the server to the client has been acknowledged by
     * the client, we needn't retain a copy of it for retransmission.  This
     * method discards any retained messages whose sequence numbers are less
     * than or equal to the parameter.
     *
     * @param seqNum  The highest numbered acknowledged message.
     */
    private void discardAcknowledgedMessages(int seqNum) {
        while (true) {
            RTCPMessage peek = myQueue.peek();
            if (peek == null || peek.seqNum > seqNum) {
                break;
            }
            myQueueBacklog -= peek.message.length();
            myQueue.remove();
        }
        if (traceFactory.comm.getDebug()) {
            traceFactory.comm.debugm(this + " queue backlog decreased to " + myQueueBacklog);
        }
    }

    /**
     * Force initialization of the secure random number generator.
     *
     * This is a kludge motivated by said initialization being very slow.
     * Ideally, any long initialization delay ought to happen at system startup
     * time, before anybody is using the system who would care.  However,
     * Java's random number generator uses lazy initialization and won't
     * actually initialize itself until the first time it is used.  In ordinary
     * use, that would be the first time somebody tried to connect.  Users
     * shouldn't be subjected to random, mysterious long delays, so generating
     * one gratuitous random number here forces the initialization cost to be
     * paid at startup time as was desired.
     */
    static void initializeRNG() {
        /* Get the initialization delay over right now */
        theRandom.nextBoolean();
    }

    /**
     * Handle loss of an underlying TCP connection.
     *
     * @param connection  The TCP connection that died.
     */
    void loseTCPConnection(Connection connection) {
        if (myLiveConnection == connection) {
            myLiveConnection = null;
            tcpConnectionDied(connection);
            myDisconnectedTimeout =
                timer.after(myDisconnectedTimeoutInterval, this::noticeDisconnectedTimeout);
        }
    }

    /**
     * Handle the expiration of the disconnected timer: if too much time has
     * passed in a disconnected state, presume that the session is lost and
     * won't be coming back.  Kill the connection, if it isn't already dead for
     * other reasons.
     */
    private void noticeDisconnectedTimeout() {
        if (!amClosing && myLiveConnection == null) {
            if (traceFactory.comm.getEvent()) {
                traceFactory.comm.eventm(this + ": disconnected session timeout");
            }
            close();
        }
    }

    /**
     * Take notice that the client session is still active.  Since we timeout
     * the session it is inactive for too long, it's good to notice activity.
     */
    private void noteClientActivity() {
        if (!amClosing) {
            myLastActivityTime = clock.millis();
        }
    }

    /**
     * React to a clock tick event on the inactivity timeout timer.
     *
     * Check to see if it has been too long since anything was received from
     * the client; if so, kill the session.
     */
    private void noticeInactivityTick() {
        long timeInactive = clock.millis() - myLastActivityTime;
        if (timeInactive > myInactivityTimeoutInterval) {
            if (traceFactory.comm.getEvent()) {
                traceFactory.comm.eventm(this + " tick: RTCP session timeout");
            }
            close();
        } else if (timeInactive > myInactivityTimeoutInterval/2) {
            if (traceFactory.comm.getDebug()) {
                traceFactory.comm.debugm(this + " tick: RTCP session acking");
            }
            String ack = mySessionFactory.makeAck(myClientSendSeqNum);
            sendMsg(ack);
        } else {
            if (traceFactory.comm.getDebug()) {
                traceFactory.comm.debugm(this + " tick: RTCP session waiting");
            }
        }
    }

    /**
     * Accept a message or messages delivered from the client.
     *
     * @param request  The RTCP request containing the message bundle to be
     *    processed.
     */
    void receiveMessage(RTCPRequest request) {
        noteClientActivity();
        if (request.clientSendSeqNum() != myClientSendSeqNum + 1) {
            traceFactory.comm.errorm(this + " expected client seq # " +
                              (myClientSendSeqNum + 1) + ", got " +
                              request.clientSendSeqNum());
            String reply =
                mySessionFactory.makeErrorReply("sequenceError");
            sendMsg(reply);
        } else {
            discardAcknowledgedMessages(request.clientRecvSeqNum());
            JsonObject message = request.nextMessage();
            while (message != null) {
                if (trMsg.getEvent()) {
                    trMsg.msgi(this, true, message);
                }
                enqueueReceivedMessage(message);
                message = request.nextMessage();
            }
            ++myClientSendSeqNum;
        }
    }

    /**
     * Resend messages to the client that were previously sent but which the
     * client has not acknowledged.
     *
     * @param seqNum  The sequence number of the most recently acknowledged
     *    message.
     */
    void replayUnacknowledgedMessages(int seqNum) {
        discardAcknowledgedMessages(seqNum);
        for (RTCPMessage elem : myQueue) {
            String messageString =
                mySessionFactory.makeMessage(elem.seqNum,
                                             myClientSendSeqNum,
                                             elem.message.sendableString());
            if (traceFactory.comm.getDebug()) {
                traceFactory.comm.debugm(this + " resend " + elem.seqNum);
            }
            myLiveConnection.sendMsg(messageString);
        }
    }

    /**
     * Send a message to the other end of the connection.
     *
     * @param message  The message to be sent.  In this version, this must be a
     *    String or a JSONLiteral.
     */
    public void sendMsg(Object message) {
        if (amClosing) {
            return;
        }
        String messageString;
        if (message instanceof JSONLiteral) {
            JSONLiteral jsonMessage = (JSONLiteral) message;
            ++myServerSendSeqNum;
            RTCPMessage qMsg =
                    new RTCPMessage(myServerSendSeqNum, jsonMessage);
            myQueueBacklog += jsonMessage.length();
            if (traceFactory.comm.getDebug()) {
                traceFactory.comm.debugm(this + " queue backlog increased to " + myQueueBacklog);
            }
            if (myQueueBacklog > mySessionFactory.sessionBacklogLimit()) {
                traceFactory.comm.eventm(this + " queue backlog limit exceeded");
                close();
            }
            myQueue.addLast(qMsg);
            messageString =
                mySessionFactory.makeMessage(myServerSendSeqNum,
                                             myClientSendSeqNum,
                                             jsonMessage.sendableString());
            if (trMsg.getDebug()) {
                trMsg.debugm(myLiveConnection + " <| " + myServerSendSeqNum + " " + myClientSendSeqNum);
            }
            if (trMsg.getEvent()) {
                trMsg.msgi(this, false, message);
            }
        } else if (message instanceof String) {
            messageString = (String) message;
            if (myLiveConnection != null) {
                if (trMsg.getDebug()) {
                    trMsg.debugm(myLiveConnection + " <| " +
                                 messageString.trim());
                }
            }
        } else {
            throw new IllegalArgumentException("Invalid message type: " + (message == null ? "null" : message.getClass().getName()));
        }
        if (myLiveConnection != null) {
            myLiveConnection.sendMsg(messageString);
        }
    }

    /**
     * Obtain this connection's session ID.
     *
     * @return the session ID number of this session.
     */
    String sessionID() {
        return mySessionID;
    }

    /**
     * Turn debug features for this connection on or off. In the case of an
     * RTCP session, debug mode involves using longer timeouts so that things
     * work on a human time scale when debugging.
     *
     * @param mode  If true, turn debug mode on; if false, turn it off.
     */
    public void setDebugMode(boolean mode) {
        myInactivityTimeoutInterval =
            mySessionFactory.sessionInactivityTimeout(mode);
        myDisconnectedTimeoutInterval =
            mySessionFactory.sessionDisconnectedTimeout(mode);
    }

    /**
     * Handle loss of the underlying TCP connection.
     *
     * @param connection  The TCP connection that died.
     *
     */
    private void tcpConnectionDied(Connection connection) {
        if (myLiveConnection == connection) {
            myLiveConnection = null;
            noteClientActivity();
            if (traceFactory.comm.getEvent()) {
                traceFactory.comm.eventm(this + " lost " + connection);
            }
        }
    }

    /**
     * Get a printable String representation of this connection.
     *
     * @return a printable representation of this connection.
     */
    public String toString() {
        String tag;
        if (myLiveConnection != null) {
            tag = myLiveConnection.toString();
        } else {
            tag = "*";
        }
        return "RTCP(" + id() + "," + tag + ")";
    }

    /**
     * Simple struct to hold outgoing messages along with their sequence
     * numbers in the message replay queue.
     */
    private static class RTCPMessage {
        int seqNum;
        JSONLiteral message;
        
        RTCPMessage(int seqNum, JSONLiteral message) {
            this.seqNum = seqNum;
            this.message = message;
        }
    }
}
