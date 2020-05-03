package org.elkoserver.foundation.net;

import org.elkoserver.json.JSONLiteral;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.elkoserver.util.ByteArrayToAscii.byteArrayToASCII;

/**
 * Byte I/O framer factory for WebSocket connections, a perverse hybrid of HTTP
 * and TCP.
 */
public class WebSocketByteIOFramerFactory implements ByteIOFramerFactory {
    private static final Base64.Encoder base64Encoder = Base64.getEncoder();

    private final Trace trMsg;

    /** The host address of the WebSocket connection point. */
    private final String myHostAddress;
    private final TraceFactory traceFactory;

    /** The host address, stripped of port number. */
    private final String myHostName;

    /** The URI of the WebSocket connection point. */
    private final String mySocketURI;

    /**
     * Constructor.
     *
     * @param msgTrace  Trace object for logging message traffic.
     */
    WebSocketByteIOFramerFactory(Trace msgTrace, String hostAddress,
                                 String socketURI, TraceFactory traceFactory)
    {
        trMsg = msgTrace;
        myHostAddress = hostAddress;
        this.traceFactory = traceFactory;
        int colonPos = hostAddress.indexOf(':');
        if (colonPos != -1) {
            myHostName = hostAddress.substring(0, colonPos);
        } else {
            myHostName = hostAddress;
        }
        mySocketURI = socketURI;
    }

    /**
     * Provide an I/O framer for a new HTTP connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    public ByteIOFramer provideFramer(MessageReceiver receiver, String label) {
        return new WebSocketFramer(receiver, label, traceFactory);
    }

    /**
     * I/O framer implementation for HTTP requests.
     */
    private class WebSocketFramer implements ByteIOFramer {
        /** The message receiver that input is being framed for. */
        private final MessageReceiver myReceiver;
        
        /** A label for the connection, for logging. */
        private final String myLabel;

        /** Input data source. */
        private final ChunkyByteArrayInputStream myIn;

        /** Lower-level framer once we start actually reading messages. */
        private JSONByteIOFramer myMessageFramer;

        /** Stage of WebSocket input reading. */
        private int myWSParseStage;

        /** Stage is: parsing method line */
        private static final int WS_STAGE_START = 1;
        /** Stage is: parsing headers */
        private static final int WS_STAGE_HEADER = 2;
        /** Stage is: parsing handshake bytes */
        private static final int WS_STAGE_HANDSHAKE = 3;
        /** Stage is: parsing message stream */
        private static final int WS_STAGE_MESSAGES = 4;

        /** HTTP request object under construction, for start handshake. */
        private final WebSocketRequest myRequest;

        /**
         * Constructor.
         */
        WebSocketFramer(MessageReceiver receiver, String label, TraceFactory traceFactory) {
            myReceiver = receiver;
            myLabel = label;
            myIn = new ChunkyByteArrayInputStream(traceFactory);
            myWSParseStage = WS_STAGE_START;
            myRequest = new WebSocketRequest();
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
                switch (myWSParseStage) {
                    case WS_STAGE_START: {
                        String line = myIn.readASCIILine();
                        if (line == null) {
                            myIn.preserveBuffers();
                            return;
                        } else if (line.length() != 0) {
                            myRequest.parseStartLine(line);
                            myWSParseStage = WS_STAGE_HEADER;
                        }
                        break;
                    }
                    case WS_STAGE_HEADER: {
                        String line = myIn.readASCIILine();
                        if (line == null) {
                            myIn.preserveBuffers();
                            return;
                        } else if (line.length() == 0) {
                            myWSParseStage = WS_STAGE_HANDSHAKE;
                        } else {
                            myRequest.parseHeaderLine(line);
                        }
                        break;
                    }
                    case WS_STAGE_HANDSHAKE: {
                        if (myRequest.header("sec-websocket-key1") != null) {
                            byte[] crazyKey = myIn.readBytes(8);
                            if (crazyKey == null) {
                                myIn.preserveBuffers();
                                return;
                            } else {
                                myRequest.setCrazyKey(crazyKey);
                            }
                        }
                        myReceiver.receiveMsg(myRequest);
                        myWSParseStage = WS_STAGE_MESSAGES;
                        myIn.enableWebSocketFraming();
                        myMessageFramer =
                            new JSONByteIOFramer(trMsg, myReceiver, myLabel,
                                                 myIn);
                        return;
                    }
                    case WS_STAGE_MESSAGES: {
                        myMessageFramer.receiveBytes(null, 0);
                        return;
                    }
                }
            }
        }

        /**
         * Generate the bytes for writing a message to a connection.  In this
         * case, a message must be a string, a WebSocketHandshake object, or an
         * HTTPError object.  A string is considered to be a serialized JSON
         * message; it should be transmitted inside a WebSocket message
         * frame. A WebSocketHandshake object contains the information for a
         * connection setup handshake; it should be transmitted as the
         * appropriate HTTP header plus junk. An HTTPError object is just what
         * it seems to be; it should be transmitted as a regular HTTP error
         * response.
         *
         * @param msg  The message to be written.
         *
         * @return a byte array containing the writable form of 'msg'.
         */
        public byte[] produceBytes(Object msg) throws IOException {
            if (msg instanceof JSONLiteral) {
                msg = ((JSONLiteral) msg).sendableString();
            }
            if (msg instanceof String) {
                String msgString = (String) msg;
                if (trMsg.getEvent()) {
                    trMsg.msgi(myLabel, false, msgString);
                }
                byte[] msgBytes = msgString.getBytes(StandardCharsets.UTF_8);
                byte[] frame = new byte[msgBytes.length + 2];
                frame[0] = 0x00;
                System.arraycopy(msgBytes, 0, frame, 1, msgBytes.length);
                frame[frame.length - 1] = (byte) 0xFF;
                if (trMsg.getDebug()) {
                    trMsg.debugm("WS sending msg: " + msg);
                }
                return frame;
            } else if (msg instanceof WebSocketHandshake) {
                WebSocketHandshake handshake = (WebSocketHandshake) msg;
                if (handshake.version() == 0) {
                    byte[] handshakeBytes = handshake.bytes();
                    String header =
                        "HTTP/1.1 101 WebSocket Protocol Handshake\r\n" +
                        "Upgrade: WebSocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Origin: http://" + myHostName + "\r\n" +
                        "Sec-WebSocket-Location: ws://" + myHostAddress +
                            mySocketURI + "\r\n" +
                        "Sec-WebSocket-Protocol: *\r\n\r\n";
                    byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
                    byte[] reply =
                        new byte[headerBytes.length + handshakeBytes.length];
                    System.arraycopy(headerBytes, 0, reply, 0,
                                     headerBytes.length);
                    System.arraycopy(handshakeBytes, 0, reply,
                                    headerBytes.length, handshakeBytes.length);
                    if (trMsg.getDebug()) {
                        trMsg.debugm("WS sending handshake:\n" + header +
                            byteArrayToASCII(handshakeBytes, 0,
                                                   handshakeBytes.length));
                    }
                    return reply;
                } else if (handshake.version() == 6) {
                    String header =
                        "HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: Websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: " +
                            base64Encoder.encodeToString(handshake.bytes()) +
                            "\r\n\r\n";
                    byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
                    if (trMsg.getDebug()) {
                        trMsg.debugm("WS sending handshake:\n" + header);
                    }
                    return headerBytes;
                } else {
                    throw new Error("unsupported WebSocket version");
                }
            } else if (msg instanceof HTTPError) {
                HTTPError error = (HTTPError) msg;
                String reply = error.messageString();
                reply = "HTTP/1.1 " + error.errorNumber() + " " +
                        error.errorString() + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: " + reply.length() + "\r\n\r\n" +
                    reply;
                if (trMsg.getDebug()) {
                    trMsg.debugm("WS sending error:\n" + reply);
                }
                return reply.getBytes(StandardCharsets.US_ASCII);
            } else {
                throw new IOException("unwritable message type: " +
                                      msg.getClass());
            }
        }
    }
}
