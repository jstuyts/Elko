package org.elkoserver.server.workshop

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.util.trace.TraceFactory

/**
 * Singleton handler for the workshop 'admin' protocol.
 *
 * The 'admin' protocol consists of these requests:
 *
 * 'reinit' - Requests the workshop to reinitialize itself.
 *
 * 'shutdown' - Requests the workshop to shut down, with an option to force
 * abrupt termination.
 *
 * @param myWorkshop The workshop for this handler.
 */
internal class AdminHandler(private val myWorkshop: Workshop, traceFactory: TraceFactory) : BasicProtocolHandler(traceFactory) {

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'admin'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "admin"

    /**
     * Handle the 'reinit' verb.
     *
     * Request that the workshop be reset.
     *
     * @param from  The administrator sending the message.
     */
    @JSONMethod
    fun reinit(from: WorkshopActor) {
        from.ensureAuthorizedAdmin()
        myWorkshop.reinit()
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Request that the workshop be shut down.
     *
     * @param from  The administrator sending the message.
     */
    @JSONMethod
    fun shutdown(from: WorkshopActor) {
        from.ensureAuthorizedAdmin()
        myWorkshop.shutdown()
    }
}
