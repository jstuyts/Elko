package org.elkoserver.run.services.webserver

import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.helper.HelpScreenException
import net.sourceforge.argparse4j.impl.Arguments
import net.sourceforge.argparse4j.inf.ArgumentParser
import net.sourceforge.argparse4j.inf.ArgumentParserException
import java.io.File
import kotlin.system.exitProcess

fun main(arguments: Array<String>) {
    val parser = createParser()
    try {
        tryToStartWebServer(parser, arguments)
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

private fun tryToStartWebServer(parser: ArgumentParser, arguments: Array<String>) {
    val parsedArguments = parser.parseArgs(arguments)
    val directory = parsedArguments.getString("directory") ?: throw IllegalStateException()
    val password = parsedArguments.getString("password") ?: throw IllegalStateException()
    val listenAddress = parsedArguments.getString("listen") ?: throw IllegalStateException()
    val portNumber = parsedArguments.getInt("port") ?: throw IllegalStateException()
    val commandWithArguments: List<String> = listOf(
            "javaw",
            "-cp",
            System.getProperty("java.class.path"),
            "org.elkoserver.run.services.webserver.JettyWebServerKt",
            directory,
            password,
            listenAddress,
            portNumber.toString())
    val jettyProcess = ProcessBuilder(commandWithArguments)
            .directory(File(System.getProperty("user.dir")))
            .start()
    try {
        Thread.sleep(1000L)
        if (!jettyProcess.isAlive) {
            exitProcess(jettyProcess.exitValue())
        }
    } catch (e: InterruptedException) {
        // No action needed. Simply exit this thread.
    }
}

private fun createParser() =
        ArgumentParsers.newFor("StartSebServer").build().apply {
            addArgument("directory")
                    .type(Arguments.fileType().verifyIsDirectory())
                    .help("The directory from which to serve files")
            addArgument("password")
                    .type(String::class.java)
                    .help("The password that can be used to shut down the server")
            addArgument("-l", "--listen")
                    .type(String::class.java)
                    .setDefault("127.0.0.1")
                    .required(false)
                    .metavar("LISTEN ADDRESS")
                    .help("The address to listen on. Use 0.0.0.0 to allow network clients to connect, but only do this on network you completely trust")
            addArgument("-p", "--port")
                    .type(Int::class.java)
                    .setDefault(8080)
                    .required(false)
                    .metavar("PORT NUMBER")
                    .help("The port number to listen on")
        }
