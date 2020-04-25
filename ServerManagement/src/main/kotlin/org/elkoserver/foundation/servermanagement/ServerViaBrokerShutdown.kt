package org.elkoserver.foundation.servermanagement

import net.sourceforge.argparse4j.helper.HelpScreenException
import net.sourceforge.argparse4j.inf.ArgumentParser
import net.sourceforge.argparse4j.inf.ArgumentParserException
import java.net.Socket
import kotlin.system.exitProcess

object ServerViaBrokerShutdown {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val parser = createParser()
        try {
            tryToShutDownServer(parser, arguments)
        } catch (argumentParserException: ArgumentParserException) {
            if (argumentParserException is HelpScreenException) {
                // No action needed. Usage has already been output.
            } else {
                println(argumentParserException.message)
                parser.printUsage()
                exitProcess(1)
            }
        }
    }

    private fun createParser() =
            createHostPortPasswordArgumentParser("ServerViaBrokerShutdown", false).apply {
                addArgument("server")
                        .required(true)
                        .help("Server name")
            }

    private fun tryToShutDownServer(parser: ArgumentParser, arguments: Array<String>) {
        val parsedArguments = parser.parseArgs(arguments)
        val hostAddress = parsedArguments.getString("host") ?: throw IllegalStateException()
        val portNumber = parsedArguments.getInt("port") ?: throw IllegalStateException()
        val password = parsedArguments.getString("password") ?: throw IllegalStateException()
        val serverName = parsedArguments.getString("server") ?: throw IllegalStateException()
        shutDownServer(hostAddress, portNumber, password, serverName)
    }

    private fun shutDownServer(hostAddress: String, portNumber: Int, password: String, serverName: String) {
        Socket(hostAddress, portNumber).use { socket ->
            val outputStream = socket.getOutputStream()
            outputStream.write("""{"to":"admin", "op":"auth", "auth":{"mode":"password", "code":"$password"}}$END_OF_COMMAND""".toUtf8())
            Thread.sleep(1000L)
            outputStream.write("""{"to":"admin", "op":"shutdown", "server":"$serverName", "kill":false}$END_OF_COMMAND""".toUtf8())
            Thread.sleep(1000L)
        }
    }
}
