package org.elkoserver.foundation.net.zmq.test

import org.elkoserver.foundation.net.NetAddr
import org.zeromq.SocketType
import org.zeromq.ZMQ
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

internal object ZeromqSender {
    @JvmStatic
    fun main(args: Array<String>) {
        val `in` = BufferedReader(InputStreamReader(System.`in`, Charset.defaultCharset()))
        var host = args[0]
        var push = true
        when {
            host.startsWith("PUSH:") -> host = "tcp://${host.substring(5)}"
            host.startsWith("PUB:") -> {
                push = false
                host = try {
                    val parsedAddr = NetAddr(host.substring(4))
                    "tcp://*:${parsedAddr.port}"
                } catch (e: IOException) {
                    println("problem setting up ZMQ connection with $host: $e")
                    return
                }
            }
            else -> host = "tcp://$host"
        }
        val context = ZMQ.context(1)
        val socket: ZMQ.Socket
        if (push) {
            socket = context.socket(SocketType.PUSH)
            println("PUSH to server at $host")
            socket.connect(host)
        } else {
            socket = context.socket(SocketType.PUB)
            println("PUB at $host")
            socket.bind(host)
        }
        var msg: StringBuilder? = null
        while (true) {
            try {
                val line = `in`.readLine()
                if (line == null) {
                    break
                } else if (line == "") {
                    if (msg != null) {
                        msg.append(" ")
                        val msgBytes = msg.toString().toByteArray(StandardCharsets.UTF_16)
                        msgBytes[msgBytes.size - 1] = 0
                        socket.send(msgBytes, 0)
                        msg = null
                    }
                } else if (msg == null) {
                    msg = StringBuilder(line)
                } else {
                    msg.append("\n").append(line)
                }
            } catch (e: IOException) {
                break
            }
        }
    }
}
