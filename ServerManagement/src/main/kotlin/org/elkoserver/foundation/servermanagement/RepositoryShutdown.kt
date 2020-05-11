package org.elkoserver.foundation.servermanagement

import java.net.Socket

object RepositoryShutdown {
    @JvmStatic
    fun main(arguments: Array<String>) {
        startHostPortPasswordProgram("RepositoryShutdown", arguments, RepositoryShutdown::shutDownServer)
    }

    private fun shutDownServer(hostAddress: String, portNumber: Int, password: String) {
        Socket(hostAddress, portNumber).use { socket ->
            val outputStream = socket.getOutputStream()
            outputStream.write("""{"to":"admin", "op":"auth", "auth":{"mode":"password", "code":"$password"}}$END_OF_COMMAND""".toUtf8())
            Thread.sleep(1000L)
            outputStream.write("""{"to":"admin", "op":"shutdown"}$END_OF_COMMAND""".toUtf8())
            Thread.sleep(1000L)
        }
    }
}
