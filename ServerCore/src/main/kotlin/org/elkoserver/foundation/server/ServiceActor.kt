package org.elkoserver.foundation.server

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.actor.RoutingActor
import org.elkoserver.foundation.actor.msgAuth
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.LinkedList

/**
 * Actor for a connection to an external service.
 *
 * @param connection  The connection for talking to this actor.
 * @param refTable  Ref table for dispatching messaes sent here.
 * @param desc  Service descriptor for the server being connected to
 * @param myServer  The server we are calling from
 */
class ServiceActor internal constructor(
        connection: Connection,
        refTable: RefTable,
        desc: ServiceDesc,
        private val myServer: Server,
        private val gorgel: Gorgel,
        commGorgel: Gorgel,
        mustSendDebugReplies: Boolean) : RoutingActor(connection, refTable, commGorgel, mustSendDebugReplies) {
    /** Optional convenience label for logging and such.  */
    private val label = "workshop-${desc.hostport}"

    /** List of service links using this actor.  */
    internal val serviceLinks = LinkedList<ServiceLink>()

    /** Provider ID of the host we are connected to.  */
    internal val providerID = desc.providerID

    /**
     * Add a service link to the list of links known to be dependent upon this
     * actor's connection.
     *
     * @param link  The new link to add.
     */
    fun addLink(link: ServiceLink) {
        serviceLinks.add(link)
        link.connectActor(this)
    }

    /**
     * Handle loss of connection from the actor.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        gorgel.i?.run { info("${this@ServiceActor} connection died: $connection $reason") }
        myServer.serviceActorDied(this)
        for (link in serviceLinks) {
            link.actorDied()
        }
    }

    /**
     * @return a printable representation of this actor.
     */
    override fun toString(): String = label

    init {
        send(msgAuth("workshop", desc.auth, myServer.serverName))
    }
}
