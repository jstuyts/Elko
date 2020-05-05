package org.elkoserver.server.director

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.util.trace.TraceFactory
import java.security.SecureRandom
import kotlin.math.abs

/**
 * Singleton handler for the director 'user' protocol.
 *
 * The 'user' protocol consists of one request:
 *
 * 'reserve' - Requests a reservation on the user's behalf for entry into a
 * particular context.
 *
 * @param myDirector  The director object for this handler.
 */
internal open class UserHandler(private val myDirector: Director, traceFactory: TraceFactory) : BasicProtocolHandler(traceFactory) {

    /**
     * Obtain the director object for this server.
     *
     * @return the director object this handler is handling for.
     */
    fun director() = myDirector

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
    @JSONMethod("protocol", "context", "user", "tag")
    fun reserve(from: DirectorActor, protocol: String, contextName: String, user: OptString, optTag: OptString) {
        from.ensureAuthorizedUser()
        val userName = user.value<String?>(null)
        val provider: Provider?
        val tag = optTag.value<String?>(null)

        /* See if somebody is serving the requested context. */
        var context = myDirector.getContext(contextName)

        /* If nobody is serving it, look for somebody serving a clone. */
        var actualContextName = contextName
        if (context == null) {
            for (clone in myDirector.contextClones(actualContextName)) {
                if (!clone.isFullClone && !clone.provider().isFull && !clone.gateIsClosed()) {
                    context = clone
                    actualContextName = clone.name()
                    break
                }
            }
        }
        if (context == null) {
            /* If nobody is serving it, pick a provider to start it up. */
            provider = myDirector.locateProvider(actualContextName, protocol, from.isInternal)
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
                from.send(msgReserve(this, actualContextName, userName, null, null, context.gateClosedReason(), tag))
                return
            } else if (context.isFull) {
                from.send(msgReserve(this, actualContextName, userName, null, null, "requested context full", tag))
                return
            } else if (context.provider().isFull) {
                from.send(msgReserve(this, actualContextName, userName, null, null, "server full", tag))
                return
            } else {
                context.provider()
            }
        }
        val hostPort = provider.hostPort(protocol)
        if (hostPort != null) {
            /* Issue reservation to provider and user. */
            val reservation = abs(theRandom.nextLong()).toString()
            provider.actor().send(msgDoReserve(myDirector.providerHandler(), actualContextName, userName, reservation))
            from.send(msgReserve(this, actualContextName, userName, hostPort, reservation, null, tag))
        } else {
            /* Sorry dude, no can do. */
            from.send(msgReserve(this, actualContextName, userName, null, null, "requested protocol not available", tag))
        }
    }

    companion object {
        /** Random number generator, for reservations.  */
        @Deprecated("Global variable")
        private val theRandom = SecureRandom()

        private fun msgDoReserve(target: Referenceable, context: String?, user: String?, reservation: String) =
                JSONLiteralFactory.targetVerb(target, "doreserve").apply {
                    addParameter("context", context)
                    addParameterOpt("user", user)
                    addParameterOpt("reservation", reservation)
                    finish()
                }

        private fun msgReserve(target: Referenceable, context: String?, user: String?, hostPort: String?, reservation: String?, deny: String?, tag: String?) =
                JSONLiteralFactory.targetVerb(target, "reserve").apply {
                    addParameter("context", context)
                    addParameterOpt("user", user)
                    addParameterOpt("hostport", hostPort)
                    addParameterOpt("reservation", reservation)
                    addParameterOpt("deny", deny)
                    addParameterOpt("tag", tag)
                    finish()
                }
    }
}
