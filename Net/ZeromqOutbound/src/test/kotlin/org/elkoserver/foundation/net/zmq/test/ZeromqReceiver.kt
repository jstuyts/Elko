package org.elkoserver.foundation.net.zmq.test

import org.elkoserver.foundation.net.NetAddr
import org.zeromq.SocketType
import org.zeromq.ZMQ
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_16

internal object ZeromqReceiver {
    /** Subscribe filter to receive all messages.  */
    private val UNIVERSAL_SUBSCRIPTION = ByteArray(0)

    private const val LINEFEED_ASCII_BYTE = '\n'.code.toByte()

    @JvmStatic
    fun main(args: Array<String>) {
        var host = args[0]
        var subscribe = false
        if (host.startsWith("SUB:")) {
            subscribe = true
            host = host.substring(4)
        } else if (host.startsWith("PULL:")) {
            host = host.substring(5)
        }
        val netAddr = try {
            NetAddr(host)
        } catch (e: IOException) {
            println("problem parsing host address: $e")
            return
        }
        val addr = if (subscribe) {
            "tcp://$host"
        } else {
            "tcp://*:${netAddr.port}"
        }
        val context = ZMQ.context(1)
        val socket: ZMQ.Socket
        if (subscribe) {
            println("subscribing to ZMQ messages from $addr")
            socket = context.socket(SocketType.SUB)
            socket.subscribe(UNIVERSAL_SUBSCRIPTION)
            socket.connect(addr)
        } else {
            println("pulling ZMQ messages at $addr")
            socket = context.socket(SocketType.PULL)
            socket.bind(addr)
        }
        while (true) {
            val data = socket.recv(0)
            if (data != null) {
                var length = data.size
                while (length > 0 && data[length - 1] == 0.toByte()) {
                    --length
                }
                while (length > 0 && data[length - 1] == LINEFEED_ASCII_BYTE) {
                    --length
                }
                val msg = String(data, 0, length, UTF_16)
                println("in: $msg\n")
            } else {
                println("null ZMQ recv, exiting")
                break
            }
        }
    }
}
