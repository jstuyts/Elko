package org.elkoserver.server.director

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.JsonObject
import org.elkoserver.util.trace.TraceFactory

/**
 * Singleton handler for the director 'provider' protocol.
 *
 * The 'provider' protocol consists of these requests:
 *
 * 'address' - Reports that the provider is speaking a particular protocol
 * at a particular address and port number.
 *
 * 'context' - Reports that a particular context has been opened or closed by
 * the sending provider.
 *
 * 'gate' - Reports that a particular context's gate has been opened or
 * closed by the sending provider.
 *
 * 'load' - Reports the provider's current load factor to the director.
 *
 * 'relay' - Requests the director to deliver an arbitrary message to a
 * context, context family, or user, by relaying through the appropriate
 * provider servers for the message target's current location.
 *
 * 'user' - Reports that a particular user has arrived or departed from a
 * context provided by the sending provider.
 *
 * 'willserve' - Reports the provider's willingness to serve a particular
 * context family.
 *
 * @param director  The Director object for this handler.
 */
internal class ProviderHandler(director: Director, traceFactory: TraceFactory) : UserHandler(director, traceFactory) {
    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'provider'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "provider"

    /**
     * Handle the 'address' verb.
     *
     * Note the availability of a provider server speaking some protocol at
     * some network address.
     *
     * @param from  The provider server announcing its availability.
     * @param protocol  The protocol it will server it with.
     * @param hostPort  Where to connect for service.
     */
    @JSONMethod("protocol", "hostport")
    fun address(from: DirectorActor, protocol: String, hostPort: String) {
        from.ensureAuthorizedProvider()
        from.provider()!!.addProtocol(protocol!!, hostPort!!)
    }

    /**
     * Handle the 'context' verb.
     *
     * Note the opening or closing of a context.
     *
     * @param from  The provider server announcing the context change.
     * @param context  The context that opened or closed.
     * @param open  true if context opened, false if it closed.
     * @param mine  true if this director was the one that told it to open.
     * @param optMaxCapacity  The optional maximum user capacity for the
     * context.
     * @param optBaseCapacity  The optional base capacity for the (clone)
     * context.
     * @param optRestricted  Optional flag indicating whether or not the
     * context is restricted (unrestricted by default).
     */
    @JSONMethod("context", "open", "yours", "maxcap", "basecap", "restricted")
    fun context(from: DirectorActor, context: String, open: Boolean,
                mine: Boolean, optMaxCapacity: OptInteger,
                optBaseCapacity: OptInteger, optRestricted: OptBoolean) {
        from.ensureAuthorizedProvider()
        if (open) {
            val maxCapacity = optMaxCapacity.value(-1)
            val baseCapacity = optBaseCapacity.value(maxCapacity)
            val restricted = optRestricted.value(false)
            from.provider()!!.noteContextOpen(context!!, mine, maxCapacity, baseCapacity, restricted)
        } else {
            from.provider()!!.noteContextClose(context!!)
        }
    }

    /**
     * Handle the 'gate' verb.
     *
     * Note the opening or closing of a context's gate.
     *
     * @param from  The provider server announcing the change
     * @param context  The context whose gate is being opened or closed
     * @param open  true if the gate is opening, false if it is closing
     * @param optReason  Optional reason string indicating why the gate is
     * closed (ignored for opening).  This will be reported to users who
     * attempt to enter when they fail.
     */
    @JSONMethod("context", "open", "reason")
    fun gate(from: DirectorActor, context: String, open: Boolean, optReason: OptString) {
        from.ensureAuthorizedProvider()
        from.provider()!!.noteContextGateSetting(context, open, optReason.value<String?>(null))
    }

    /**
     * Handle the 'load' verb.
     *
     * Note a provider's load factor.
     *
     * @param from  The provider server announcing its load.
     * @param factor  The load factor.
     */
    @JSONMethod("factor")
    fun load(from: DirectorActor, factor: Double) {
        from.ensureAuthorizedProvider()
        from.provider()!!.setLoadFactor(factor)
    }

    /**
     * Handle the 'relay' verb.
     *
     * Request that a message be relayed to an user or context.
     *
     * @param from  The provider sending the message.
     * @param context  The context to be broadcast to.
     * @param user  The user to be broadcast to.
     * @param msg  The message to relay to them.
     */
    @JSONMethod("context", "user", "msg")
    fun relay(from: DirectorActor, context: OptString, user: OptString, msg: JsonObject) {
        from.ensureAuthorizedProvider()
        director().doRelay(from, context!!, user!!, msg!!)
    }

    /**
     * Handle the 'user' verb.
     *
     * Note the arrival or departure of an user.
     *
     * @param from  The provider server announcing the context change.
     * @param context  The context that the user entered or exited.
     * @param user  The user who entered or exited.
     * @param on  true on entry, false on exit.
     */
    @JSONMethod("context", "user", "on")
    fun user(from: DirectorActor, context: String, user: String, on: Boolean) {
        from.ensureAuthorizedProvider()
        if (on) {
            from.provider()!!.noteUserEntry(context!!, user!!)
        } else {
            from.provider()!!.noteUserExit(context!!, user!!)
        }
    }

    /**
     * Handle the 'willserve' verb.
     *
     * Note the availability of a provider to serve some class of contexts.
     *
     * @param from  The provider server announcing its availability.
     * @param context  The context family it will serve.
     * @param capacity  Optional limit on the number of clients it will serve.
     * @param restricted  Optional flag restricting reservations for this
     * class of contexts to reservations made internally.
     */
    @JSONMethod("context", "capacity", "restricted")
    fun willserve(from: DirectorActor, context: String, capacity: OptInteger, restricted: OptBoolean) {
        from.ensureAuthorizedProvider()
        from.provider()!!.addService(context!!, capacity.value(-1), restricted.value(false))
    }
}
