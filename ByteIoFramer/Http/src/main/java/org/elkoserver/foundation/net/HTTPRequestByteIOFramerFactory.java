package org.elkoserver.foundation.net;

import org.elkoserver.util.trace.TraceFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Byte I/O framer factory for HTTP requests.  The framing rules implemented
 * by this class are the message formatting rules for HTTP requests and
 * responses as described by "RFC 2616: Hypertext Transfer Protocol --
 * HTTP/1.1", except that chunked transfer coding is not supported.
 */
public class HTTPRequestByteIOFramerFactory implements ByteIOFramerFactory {
    private TraceFactory traceFactory;

    /**
     * Constructor.
     */
    HTTPRequestByteIOFramerFactory(TraceFactory traceFactory) {
        this.traceFactory = traceFactory;
    }

    /**
     * Provide an I/O framer for a new HTTP connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    public ByteIOFramer provideFramer(MessageReceiver receiver, String label) {
        return new HTTPRequestFramer(receiver, label, traceFactory);
    }

    /**
     * I/O framer implementation for HTTP requests.
     */
    private static class HTTPRequestFramer implements ByteIOFramer {
        /** The message receiver that input is being framed for. */
        private MessageReceiver myReceiver;
        
        /** A label for the connection, for logging. */
        private String myLabel;

        /** Input data source. */
        private ChunkyByteArrayInputStream myIn;
        private TraceFactory traceFactory;

        /** Stage of HTTP request reading. */
        private int myHTTPParseStage;

        /** Stage is: parsing method line */
        private static final int HTTP_STAGE_START = 1;
        /** Stage is: parsing headers */
        private static final int HTTP_STAGE_HEADER = 2;
        /** Stage is: parsing body */
        private static final int HTTP_STAGE_BODY = 3;

        /** HTTP request object under construction. */
        private HTTPRequest myRequest;

        /**
         * Constructor.
         */
        HTTPRequestFramer(MessageReceiver receiver, String label, TraceFactory traceFactory) {
            myReceiver = receiver;
            myLabel = label;
            myIn = new ChunkyByteArrayInputStream(traceFactory);
            this.traceFactory = traceFactory;
            myHTTPParseStage = HTTP_STAGE_START;
            myRequest = new HTTPRequest();
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
                switch (myHTTPParseStage) {
                    case HTTP_STAGE_START: {
                        String line = myIn.readASCIILine();
                        if (line == null) {
                            myIn.preserveBuffers();
                            return;
                        } else if (line.length() != 0) {
                            myRequest.parseStartLine(line);
                            myHTTPParseStage = HTTP_STAGE_HEADER;
                        }
                        break;
                    }
                    case HTTP_STAGE_HEADER: {
                        String line = myIn.readASCIILine();
                        if (line == null) {
                            myIn.preserveBuffers();
                            return;
                        } else if (line.length() == 0) {
                            myHTTPParseStage = HTTP_STAGE_BODY;
                        } else {
                            myRequest.parseHeaderLine(line);
                        }
                        break;
                    }
                    case HTTP_STAGE_BODY: {
                        int bodyLen = myRequest.contentLength();
                        if (bodyLen > Communication.MAX_MSG_LENGTH) {
                            throw new IOException("message too large: " +
                                bodyLen + " > " +
                                    Communication.MAX_MSG_LENGTH);
                        } else if (bodyLen > 0) {
                            if (myIn.available() < bodyLen) {
                                myIn.preserveBuffers();
                                return;
                            } else {
                                myIn.updateUsefulByteCount(bodyLen);
                                StringBuilder contentBuilder = new StringBuilder(bodyLen);
                                InputStreamReader isr = new InputStreamReader(myIn, StandardCharsets.UTF_8);
                                int character = isr.read();
                                while (character != -1) {
                                    contentBuilder.append((char)character);
                                    character = isr.read();
                                }
                                myRequest.setContent(contentBuilder.toString());
                            }
                        }
                        myReceiver.receiveMsg(myRequest);
                        myRequest = new HTTPRequest();
                        myHTTPParseStage = HTTP_STAGE_START;
                        break;
                    }
                }
            }
        }
        
        /**
         * Determine the number of bytes in the UTF-8 encoding of a string.
         *
         * @param str  The string whose encoding length is of interest.
         *
         * @return the number of bytes it would take to encode the string in
         *    UTF-8.
         */
        private int utf8Length(String str) {
            return str.getBytes(StandardCharsets.UTF_8).length; // VERY inefficient, OK for testing
        }

        /**
         * Generate the bytes for writing a message to a connection.
         *
         * @param message  The message to be written.  In this case, the
         *    message must be a String or an HTTPError.
         *
         * @return a byte array containing the writable form of 'message'.
         */
        public byte[] produceBytes(Object message) throws IOException {
            String reply;

            if (message instanceof String) {
                reply = (String) message;
                if (traceFactory.comm.getVerbose()) {
                    traceFactory.comm.verbosem("to=" + myLabel +
                                        " writeMessage=" + reply.length());
                }
                reply = "HTTP/1.1 200 OK\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Length: " + utf8Length(reply) + "\r\n\r\n" +
                    reply;
            } else if (message instanceof HTTPError) {
                HTTPError error = (HTTPError) message;
                reply = error.messageString();
                reply = "HTTP/1.1 " + error.errorNumber() + " " +
                    error.errorString() + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: " + utf8Length(reply) + "\r\n\r\n" +
                    reply;
            } else if (message instanceof HTTPOptionsReply) {
                HTTPOptionsReply options = (HTTPOptionsReply) message;
                reply = "HTTP/1.1 200 OK\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Max-Age: 31536000\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                    options.headersHeader() +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: 0\r\n" +
                    "\r\n";
            } else {
                throw new IOException("unwritable message type: " +
                                      message.getClass());
            }
            if (traceFactory.comm.getDebug()) {
                traceFactory.comm.debugm("HTTP sending:\n" + reply);
            }
            return reply.getBytes(StandardCharsets.UTF_8);
        }
    }
}
