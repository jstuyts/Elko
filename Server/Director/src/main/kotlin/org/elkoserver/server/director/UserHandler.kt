package org.elkoserver.server.director

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.Random
import kotlin.math.abs

/**
 * Singleton handler for the director 'user' protocol.
 *
 * The 'user' protocol consists of one request:
 *
 * 'reserve' - Requests a reservation on the user's behalf for entry into a
 * particular context.
 *
 * @param director  The director object for this handler.
 * @param myRandom Random number generator, for reservations.
 */
internal open class UserHandler(protected val director: Director, commGorgel: Gorgel, private val myRandom: Random) : BasicProtocolHandler(commGorgel) {

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'director'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "director"

    /**
     * Handle the 'reserve' verb.
     *
     * User requests a reservation with a provider server.
     *
     * @param from  The user asking for the reservation.
     * @param protocol  The protocol it wants to use.
     * @param contextName  The context it is seeking.
     * @param user  The user who is asking for this.
     * @param optTag  Optional tag for requestor to match
     */
    @JsonMethod("protocol", "context", "user", "tag")
    fun reserve(from: DirectorActor, protocol: String, contextName: String, user: OptString, optTag: OptString) {
        from.ensureAuthorizedUser()
        val userName = user.value<String?>(null)
        val provider: Provider?
        val tag = optTag.value<String?>(null)

        /* See if somebody is serving the requested context. */
        var context = director.getContext(contextName)

        /* If nobody is serving it, look for somebody serving a clone. */
        var actualContextName = contextName
        if (context == null) {
            for (clone in director.contextClones(actualContextName)) {
                if (!clone.isFullClone && !clone.provider.isFull && !clone.gateIsClosed()) {
                    context = clone
                    actualContextName = clone.name
                    break
                }
            }
        }
        if (context == null) {
            /* If nobody is serving it, pick a provider to start it up. */
            provider = director.locateProvider(actualContextName, protocol, from.isInternal)
            if (provider == null) {
                from.send(msgReserve(this, actualContextName, userName, null, null, "unable to find suitable server", tag))
                return
            }
        } else {
            /* If somebody is serving it, make sure it's really usable. */
            provider = if (context.isRestricted && !from.isInternal) {
                from.send(msgReserve(this, actualContextName, userName, null, null, "unable to find suitable server", tag))
                return
            } else if (context.gateIsClosed()) {
                from.send(msgReserve(this, actualContextName, userName, null, null, context.gateClosedReason, tag))
                return
            } else if (context.isFull) {
                from.send(msgReserve(this, actualContextName, userName, null, null, "requested context full", tag))
                return
            } else if (context.provider.isFull) {
                from.send(msgReserve(this, actualContextName, userName, null, null, "server full", tag))
                return
            } else {
                context.provider
            }
        }
        val hostPort = provider.hostPort(protocol)
        if (hostPort != null) {
            /* Issue reservation to provider and user. */
            val reservation = abs(myRandom.nextLong()).toString()
            provider.actor.send(msgDoReserve(director.providerHandler, actualContextName, userName, reservation))
            from.send(msgReserve(this, actualContextName, userName, hostPort, reservation, null, tag))
        } else {
            /* Sorry dude, no can do. */
            from.send(msgReserve(this, actualContextName, userName, null, null, "requested protocol not available", tag))
        }
    }
}
