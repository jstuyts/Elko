package org.elkoserver.foundation.net;

import com.grack.nanojson.JsonParserException;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JsonObject;
import org.elkoserver.json.JsonObjectSerialization;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.elkoserver.json.JsonParsing.jsonObjectFromReader;

/**
 * I/O framer implementation for JSON messages.
 */
public class JSONByteIOFramer implements ByteIOFramer {
    /** The message receiver input is being framed for. */
    private MessageReceiver myReceiver;
    
    /** A label for the connection, for logging. */
    private String myLabel;
    
    /** Input data source. */
    private ChunkyByteArrayInputStream myIn;
    
    /** Message input currently in progress. */
    private StringBuilder myMsgBuffer;
    
    /** Trace object for logging message traffic. */
    private Trace trMsg;

    /**
     * Constructor.
     */
    public JSONByteIOFramer(Trace msgTrace, MessageReceiver receiver,
                            String label, TraceFactory traceFactory)
    {
        this(msgTrace, receiver, label, new ChunkyByteArrayInputStream(traceFactory));
    }
    
    /**
     * Constructor with explicit input.
     */
    public JSONByteIOFramer(Trace msgTrace, MessageReceiver receiver,
                            String label, ChunkyByteArrayInputStream in)
    {
        trMsg = msgTrace;
        myReceiver = receiver;
        myLabel = label;
        myMsgBuffer = new StringBuilder(1000);
        myIn = in;
    }
    
    
    /**
     * Process bytes of data received.
     *
     * @param data   The bytes received.
     * @param length  Number of usable bytes in 'data'.  End of input is
     *    indicated by passing a 'length' value of 0.
     */
    public void receiveBytes(byte[] data, int length) throws IOException {
        if (data != null) {
            myIn.addBuffer(data, length);
        }
        
        while (true) {
            String line = myIn.readUTF8Line();
            if (line == null) {
                break;
            }
            if (line.length() == 0) {
                String msgString = myMsgBuffer.toString();
                if (trMsg.getEvent()) {
                    trMsg.msgi(myLabel, true, msgString);
                }
                // FIXME: Do not end because of no more characters at end of string. Instead fail gracefully.
                StringReader msgReader = new StringReader(msgString);
                boolean needsFurtherParsing = true;
                while (needsFurtherParsing) {
                    try {
                        JsonObject obj = jsonObjectFromReader(msgReader);
                        if (obj == null) {
                            needsFurtherParsing = false;
                        } else {
                            myReceiver.receiveMsg(obj);
                        }
                    } catch (JsonParserException e) {
                        needsFurtherParsing = false;
                        if (Communication.TheDebugReplyFlag) {
                            myReceiver.receiveMsg(e);
                        }
                        if (trMsg.getWarning()) {
                            trMsg.warningm("syntax error in JSON message: " +
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
        }
        
        myIn.preserveBuffers();
    }
    
    /**
     * Generate the bytes for writing a message to a connection.
     *
     * @param message  The message to be written.  In this implementation,
     *    the message must be a string.
     *
     * @return a byte array containing the writable form of 'message'.
     */
    public byte[] produceBytes(Object message) throws IOException {
        String messageString;
        
        if (message instanceof JSONLiteral) {
            messageString = ((JSONLiteral) message).sendableString();
        } else if (message instanceof JsonObject) {
            messageString = JsonObjectSerialization.sendableString((JsonObject) message);
        } else if (message instanceof String) {
            messageString = (String) message;
        } else {
            throw new IOException("invalid message object class for write");
        }
        if (trMsg.getEvent()) {
            trMsg.msgi(myLabel, false, messageString);
        }
        messageString += "\n\n";
        return messageString.getBytes(StandardCharsets.UTF_8);
    }
}
