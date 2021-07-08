package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.ConnectionSetupFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.LinkedList

class ListenersStarter(
    private val myProps: ElkoProperties,
    private val serviceName: String,
    private val gorgel: Gorgel,
    private val listenerConfigurationFromPropertiesFactory: ListenerConfigurationFromPropertiesFactory,
    private val connectionSetupFactoriesByCode: Map<String, ConnectionSetupFactory>,
    private val server: Server,
) {
    /**
     * Start listening for connections on some port.
     *
     * @param propRoot  Prefix string for all the properties describing the
     * listener that is to be started.
     * @param host  The host:port string for the port to listen on.
     * @param metaFactory   Object that will provide a message handler factory
     * for connections made to this listener.
     *
     * @return host description for the listener that was started, or null if
     * the operation failed for some reason.
     */
    private fun startOneListener(propRoot: String, host: String, metaFactory: ServiceFactory) =
        startOneListener(propRoot, host, metaFactory, listenerConfigurationFromPropertiesFactory.read(propRoot))

    private fun startOneListener(
        propRoot: String,
        host: String,
        metaFactory: ServiceFactory,
        configuration: ListenerConfiguration
    ) =
        configuration.run {
            val serviceNames: MutableList<String> = LinkedList()
            val actorFactory = metaFactory.provideFactory(propRoot, auth, allow, serviceNames, protocol)
            val connectionSetup =
                connectionSetupFactoriesByCode[protocol]?.create(label, host, auth, secure, propRoot, actorFactory)
            if (connectionSetup == null) {
                gorgel.error("unknown value for $propRoot.protocol: $protocol, listener $propRoot not started")
                throw IllegalStateException()
            }
            connectionSetup.startListener()
            serviceNames
                .map { "$it${serviceName}" }
                .forEach { server.registerService(ServiceDesc(it, host, protocol, label, auth, null, -1)) }
            HostDesc(protocol, secure, connectionSetup.serverAddress, auth, -1)
        }

    /**
     * Start listening for connections on all the ports that are configured.
     *
     * @param propRoot  Prefix string for all the properties describing the
     * listeners that are to be started.
     * @param serviceFactory   Object to provide message handler factories for
     * the new listeners.
     *
     * @return the number of ports that were configured.
     */
    fun startListeners(propRoot: String, serviceFactory: ServiceFactory): List<HostDesc> {
        var listenerPropRoot = propRoot
        var listenerCount = 0
        val theListeners: MutableList<HostDesc> = LinkedList()
        var hostName = myProps.getProperty("$listenerPropRoot.host")
        while (hostName != null) {
            val listener = startOneListener(listenerPropRoot, hostName, serviceFactory)
            theListeners.add(listener)

            listenerPropRoot = propRoot + ++listenerCount

            hostName = myProps.getProperty("$listenerPropRoot.host")
        }
        return theListeners
    }
}
