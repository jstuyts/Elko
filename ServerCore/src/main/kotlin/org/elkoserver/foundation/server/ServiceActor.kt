package org.elkoserver.foundation.server

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.actor.RoutingActor
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.util.LinkedList

/**
 * Actor for a connection to an external service.
 *
 * @param connection  The connection for talking to this actor.
 * @param refTable  Ref table for dispatching messaes sent here.
 * @param desc  Service descriptor for the server being connected to
 * @param myServer  The server we are calling from
 */
class ServiceActor internal constructor(connection: Connection?, refTable: RefTable?, desc: ServiceDesc,
                                        private val myServer: Server, traceFactory: TraceFactory?) : RoutingActor(connection!!, refTable!!, traceFactory!!) {
    /** Optional convenience label for logging and such.  */
    private val myLabel: String

    /** List of service links using this actor.  */
    private val myServiceLinks = LinkedList<ServiceLink>()

    /** Provider ID of the host we are connected to.  */
    private val myProviderID: Int

    /** Trace object for diagnostics.  */
    private val tr: Trace

    /**
     * Add a service link to the list of links known to be dependent upon this
     * actor's connection.
     *
     * @param link  The new link to add.
     */
    fun addLink(link: ServiceLink) {
        myServiceLinks.add(link)
        link.connectActor(this)
    }

    /**
     * Handle loss of connection from the actor.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        tr.eventm("$this connection died: $connection $reason")
        myServer.serviceActorDied(this)
        for (link in myServiceLinks) {
            link.actorDied()
        }
    }

    /**
     * Return this actor's label.
     */
    fun label() = myLabel

    /**
     * Get the Broker-issed provider ID of the server at the other end of
     * this actor's connection.
     *
     * @return this actor's provider ID.
     */
    fun providerID() = myProviderID

    /**
     * Obtain a list of the services currently linked to through this actor.
     *
     * @return a list of this actor's service links.
     */
    fun serviceLinks() = myServiceLinks

    /**
     * @return a printable representation of this actor.
     */
    override fun toString() = myLabel

    init {
        send(msgAuth("workshop", desc.auth(), myServer.serverName()))
        myLabel = "workshop-${desc.hostport()}"
        myProviderID = desc.providerID()
        tr = myServer.trace()
    }
}
