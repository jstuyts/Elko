package org.elkoserver.run.services.webserver

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.HandlerList
import org.eclipse.jetty.server.handler.ResourceHandler
import org.eclipse.jetty.server.handler.ShutdownHandler
import java.net.InetSocketAddress


fun main(arguments: Array<String>) {
    val directory = arguments[0]
    val password = arguments[1]
    val listenAddress = arguments[2]
    val portNumber = arguments[3].toInt()
    val server = Server(InetSocketAddress(listenAddress, portNumber))
    server.handler = HandlerList(
            ResourceHandler().apply {
                resourceBase = directory
            },
            ShutdownHandler(password))

    server.start()
    server.join()
}
