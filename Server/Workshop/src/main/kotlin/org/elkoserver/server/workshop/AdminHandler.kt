package org.elkoserver.server.workshop

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.util.trace.slf4j.Gorgel

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
internal class AdminHandler(private val myWorkshop: Workshop, commGorgel: Gorgel) : BasicProtocolHandler(commGorgel) {

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
    @JsonMethod
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
    @JsonMethod
    fun shutdown(from: WorkshopActor) {
        from.ensureAuthorizedAdmin()
        myWorkshop.shutdown()
    }
}
