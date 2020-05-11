package org.elkoserver.foundation.servermanagement

import java.net.Socket

object ContextShutdown {

    @JvmStatic
    fun main(arguments: Array<String>) {
        startHostPortPasswordProgram("ContextShutdown", arguments, ContextShutdown::shutDownServer, true)
    }

    private fun shutDownServer(hostAddress: String, portNumber: Int, password: String) {
        Socket(hostAddress, portNumber).use { socket ->
            socket.getOutputStream().write("""{"to":"session", "op":"shutdown"${toPasswordProperty(password)}}$END_OF_COMMAND""".toUtf8())
            Thread.sleep(1000L)
        }
    }
}
