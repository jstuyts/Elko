package org.elkoserver.foundation.byteioframer.json

import com.grack.nanojson.JsonParserException
import org.elkoserver.foundation.byteioframer.ByteIOFramer
import org.elkoserver.foundation.byteioframer.ChunkyByteArrayInputStream
import org.elkoserver.foundation.byteioframer.MessageReceiver
import org.elkoserver.foundation.net.Communication
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JsonObject
import org.elkoserver.json.JsonObjectSerialization.sendableString
import org.elkoserver.json.JsonParsing.jsonObjectFromReader
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException
import java.io.StringReader
import java.nio.charset.StandardCharsets

/**
 * I/O framer implementation for JSON messages.
 */
class JSONByteIOFramer(
        private val gorgel: Gorgel,
        private val myReceiver: MessageReceiver,
        private val myLabel: String,
        private val myIn: ChunkyByteArrayInputStream,
        private val mustSendDebugReplies: Boolean) : ByteIOFramer {

    /** Message input currently in progress.  */
    private val myMsgBuffer = StringBuilder(1000)

    /**
     * Constructor.
     */
    constructor(gorgel: Gorgel, receiver: MessageReceiver, label: String, inputGorgel: Gorgel, mustSendDebugReplies: Boolean)
            : this(gorgel, receiver, label, ChunkyByteArrayInputStream(inputGorgel), mustSendDebugReplies)

    /**
     * Process bytes of data received.
     *
     * @param data   The bytes received.
     * @param length  Number of usable bytes in 'data'.  End of input is
     * indicated by passing a 'length' value of 0.
     */
    @Throws(IOException::class)
    override fun receiveBytes(data: ByteArray, length: Int) {
        myIn.addBuffer(data, length)
        while (true) {
            val line = myIn.readUTF8Line() ?: break
            if (line.isEmpty()) {
                val msgString = myMsgBuffer.toString()
                gorgel.i?.run { info("$myLabel -> $msgString") }
                // FIXME: Do not end because of no more characters at end of string. Instead fail gracefully.
                val msgReader = StringReader(msgString)
                var needsFurtherParsing = true
                while (needsFurtherParsing) {
                    try {
                        val obj = jsonObjectFromReader(msgReader)
                        if (obj == null) {
                            needsFurtherParsing = false
                        } else {
                            myReceiver.receiveMsg(obj)
                        }
                    } catch (e: JsonParserException) {
                        needsFurtherParsing = false
                        if (mustSendDebugReplies) {
                            myReceiver.receiveMsg(e)
                        }
                        gorgel.warn("syntax error in JSON message: ${e.message}")
                    }
                }
                myMsgBuffer.setLength(0)
            } else if (myMsgBuffer.length + line.length >
                    Communication.MAX_MSG_LENGTH) {
                throw IOException("input too large (limit ${Communication.MAX_MSG_LENGTH} bytes)")
            } else {
                myMsgBuffer.append(' ')
                myMsgBuffer.append(line)
            }
        }
        myIn.preserveBuffers()
    }

    /**
     * Generate the bytes for writing a message to a connection.
     *
     * @param message  The message to be written.  In this implementation,
     * the message must be a string.
     *
     * @return a byte array containing the writable form of 'message'.
     */
    @Throws(IOException::class)
    override fun produceBytes(message: Any): ByteArray {
        var messageString = if (message is JSONLiteral) {
            message.sendableString()
        } else if (message is JsonObject) {
            sendableString(message)
        } else if (message is String) {
            message
        } else {
            throw IOException("invalid message object class for write")
        }
        gorgel.i?.run { info("$myLabel <- $message") }
        messageString += "\n\n"
        return messageString.toByteArray(StandardCharsets.UTF_8)
    }
}
