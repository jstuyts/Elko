package org.elkoserver.foundation.servermanagement

import java.net.Socket

object DirectorShutdown {
    @JvmStatic
    fun main(arguments: Array<String>) {
        startHostPortPasswordProgram("DirectorShutdown", arguments, DirectorShutdown::shutDownServer, true)
    }

    private fun shutDownServer(hostAddress: String, portNumber: Int, password: String) {
        Socket(hostAddress, portNumber).use { socket ->
            val outputStream = socket.getOutputStream()
            outputStream.write("""{"to":"admin", "op":"auth", "auth":{"mode":""".toUtf8())
            if (isNoPasswordIndicator(password)) {
                outputStream.write(""""open"""".toUtf8())
            } else {
                outputStream.write(""""password", "code":"$password"""".toUtf8())
            }
            outputStream.write("""}}$END_OF_COMMAND""".toUtf8())
            Thread.sleep(1000L)
            outputStream.write("""{"to":"admin", "op":"shutdown", "director":true}$END_OF_COMMAND""".toUtf8())
            Thread.sleep(1000L)
        }
    }
}
