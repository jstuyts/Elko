package org.elkoserver.run.services.webserver

import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.helper.HelpScreenException
import net.sourceforge.argparse4j.inf.ArgumentParser
import net.sourceforge.argparse4j.inf.ArgumentParserException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.exitProcess

fun main(arguments: Array<String>) {
    val parser = createParser()
    try {
        tryToStopWebServer(parser, arguments)
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

fun tryToStopWebServer(parser: ArgumentParser, arguments: Array<String>) {
    val parsedArguments = parser.parseArgs(arguments)
    val password = parsedArguments.getString("password") ?: throw IllegalStateException()
    val portNumber = parsedArguments.getInt("port") ?: throw IllegalStateException()
    val url = URL("http://localhost:$portNumber/shutdown?token=$password")
    (url.openConnection() as HttpURLConnection).run {
        requestMethod = "POST"
        responseCode
    }
}

private fun createParser() =
        ArgumentParsers.newFor("StartSebServer").build().apply {
            addArgument("password")
                    .type(String::class.java)
                    .help("The shutdown password")
            addArgument("-p", "--port")
                    .type(Int::class.java)
                    .setDefault(8080)
                    .required(false)
                    .metavar("PORT NUMBER")
                    .help("The port number the server is listening on")
        }
