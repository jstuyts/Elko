package org.elkoserver.foundation.servermanagement

import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.helper.HelpScreenException
import net.sourceforge.argparse4j.inf.ArgumentParser
import net.sourceforge.argparse4j.inf.ArgumentParserException
import kotlin.system.exitProcess

internal fun startHostPortPasswordProgram(programName: String, arguments: Array<String>, programStarter: (String, Int, String) -> Unit, allowNoPassword: Boolean = false) {
    val parser = createHostPortPasswordArgumentParser(programName, allowNoPassword)
    startProgram(parser, arguments, programStarter)
}

internal fun createHostPortPasswordArgumentParser(programName: String, allowNoPassword: Boolean) =
        ArgumentParsers.newFor(programName).build().apply {
            addArgument("host")
                    .required(true)
                    .help("Host address")
            addArgument("port")
                    .type(Integer::class.java)
                    .required(true)
                    .help("TCP port number")
            addArgument("password")
                    .required(true)
                    .help("Password${if (allowNoPassword) ", - = no password" else ""}")
        }

private fun startProgram(parser: ArgumentParser, arguments: Array<String>, programStarter: (String, Int, String) -> Unit) {
    try {
        tryToStartProgram(parser, arguments, programStarter)
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

private fun tryToStartProgram(parser: ArgumentParser, arguments: Array<String>, programStarter: (String, Int, String) -> Unit) {
    val parsedArguments = parser.parseArgs(arguments)
    val hostAddress = parsedArguments.getString("host") ?: throw IllegalStateException()
    val portNumber = parsedArguments.getInt("port") ?: throw IllegalStateException()
    val password = parsedArguments.getString("password") ?: throw IllegalStateException()
    programStarter(hostAddress, portNumber, password)
}
