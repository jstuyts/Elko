package org.elkoserver.foundation.net;

import com.grack.nanojson.JsonParserException;
import org.elkoserver.json.JsonObject;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.elkoserver.json.JsonParsing.jsonObjectFromString;

/**
 * Byte I/O framer factory for RTCP requests.  The framing rule used is: read
 * one line &amp; interpret it as an RTCP request line.  If the request is not a
 * message delivery, then framing is complete at this point.  If it *is* a
 * message delivery, then continue, following exactly the message framing rule
 * implemented by the <code>JSONByteIOFramerFactory</code> class: read a block of
 * one or more non-empty lines terminated by an empty line (i.e., by two
 * successive newlines).
 *
 * <p>On recognition of an RTCP request as a message delivery, each block
 * matching the JSON framing rule is regarded as a parseable unit; that is, it
 * is expected to contain one or more syntactically complete JSON messages.
 * The entire block is read into an internal buffer, then parsed for JSON
 * messages that are fed to the receiver.</p>
 *
 * <p>On output, each thing being sent is always in the form of a string by the
 * time this class gets its hands on it, so output framing consists of merely
 * ensuring that the proper character encoding is used.</p>
 */
public class RTCPRequestByteIOFramerFactory implements ByteIOFramerFactory {
    /** Trace object for logging message traffic. */
    private Trace trMsg;
    private TraceFactory traceFactory;

    /**
     * Constructor.
     */
    RTCPRequestByteIOFramerFactory(Trace msgTrace, TraceFactory traceFactory) {
        trMsg = msgTrace;
        this.traceFactory = traceFactory;
    }

    /**
     * Provide an I/O framer for a new RTCP connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    public ByteIOFramer provideFramer(MessageReceiver receiver, String label) {
        return new RTCPRequestFramer(receiver, label, traceFactory);
    }

    /**
     * I/O framer implementation for RTCP requests.
     */
    private class RTCPRequestFramer implements ByteIOFramer {
        /** The message receiver that input is being framed for. */
        private MessageReceiver myReceiver;
        
        /** A label for the connection, for logging. */
        private String myLabel;

        /** Input data source. */
        private ChunkyByteArrayInputStream myIn;

        /** JSON message input currently in progress. */
        private StringBuilder myMsgBuffer;

        /** Stage of RTCP request reading. */
        private int myRTCPParseStage;

        /** Stage is: parsing request line */
        private static final int RTCP_STAGE_REQUEST = 1;
        /** Stage is: parsing JSON message block */
        private static final int RTCP_STAGE_MESSAGES = 2;

        /** RTCP request object under construction. */
        private RTCPRequest myRequest;

        /**
         * Constructor.
         */
        RTCPRequestFramer(MessageReceiver receiver, String label, TraceFactory traceFactory) {
            myReceiver = receiver;
            myLabel = label;
            myMsgBuffer = new StringBuilder(1000);
            myIn = new ChunkyByteArrayInputStream(traceFactory);
            myRTCPParseStage = RTCP_STAGE_REQUEST;
            myRequest = new RTCPRequest();
        }
        
        /**
         * Process bytes of data received.
         *
         * @param data   The bytes received.
         * @param length  Number of usable bytes in 'data'.  End of input is
         *    indicated by passing a 'length' value of 0.
         */
        public void receiveBytes(byte[] data, int length) throws IOException {
            myIn.addBuffer(data, length);

            while (true) {
                switch (myRTCPParseStage) {
                    case RTCP_STAGE_REQUEST: {
                        String line = myIn.readASCIILine();
                        if (line == null) {
                            myIn.preserveBuffers();
                            return;
                        } else if (line.length() != 0) {
                            if (trMsg.getDebug()) {
                                trMsg.debugm(myLabel + " |> " + line);
                            }
                            myRequest.parseRequestLine(line);
                            if (!myRequest.isComplete()) {
                                myRTCPParseStage = RTCP_STAGE_MESSAGES;
                            }
                        }
                        break;
                    }
                    case RTCP_STAGE_MESSAGES: {
                        String line = myIn.readUTF8Line();
                        if (line == null) {
                            myIn.preserveBuffers();
                            return;
                        }
                        if (line.length() == 0) {
                            boolean needsFurtherParsing = true;
                            while (needsFurtherParsing) {
                                try {
                                    JsonObject obj = jsonObjectFromString(myMsgBuffer.toString());
                                    if (obj == null) {
                                        needsFurtherParsing = false;
                                    } else {
                                        myRequest.addMessage(obj);
                                    }
                                } catch (JsonParserException e) {
                                    needsFurtherParsing = false;
                                    if (Communication.TheDebugReplyFlag) {
                                        myRequest.noteProblem(e);
                                    }
                                    if (trMsg.getWarning()) {
                                        trMsg.warningm(
                                            "syntax error in JSON message: " +
                                            e.getMessage());
                                    }
                                }
                            }
                            myMsgBuffer.setLength(0);
                        } else if (myMsgBuffer.length() + line.length() >
                                Communication.MAX_MSG_LENGTH) {
                            throw new IOException("input too large (limit " +
                                    Communication.MAX_MSG_LENGTH + " bytes)");
                        } else {
                            myMsgBuffer.append(' ');
                            myMsgBuffer.append(line);
                        }
                        break;
                    }
                }
                if (myRequest.isComplete()) {
                    myReceiver.receiveMsg(myRequest);
                    myRequest = new RTCPRequest();
                    myRTCPParseStage = RTCP_STAGE_REQUEST;
                }
            }
        }
        
        /**
         * Generate the bytes for writing a message to a connection.
         *
         * @param message  The message to be written.  In this case, the
         *    message must be a String.
         *
         * @return a byte array containing the writable form of 'message'.
         */
        public byte[] produceBytes(Object message) throws IOException {
            String reply;

            if (message instanceof String) {
                reply = (String) message;
                if (trMsg.getVerbose()) {
                    trMsg.verbosem("to=" + myLabel + " writeMessage=" +
                                   reply.length());
                }
            } else {
                throw new IOException("unwritable message type: " +
                                      message.getClass());
            }
            if (trMsg.getDebug()) {
                trMsg.debugm("RTCP sending:\n" + reply);
            }
            return reply.getBytes(StandardCharsets.UTF_8);
        }
    }
}
