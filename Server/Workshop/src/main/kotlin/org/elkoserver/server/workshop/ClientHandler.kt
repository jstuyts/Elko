package org.elkoserver.server.workshop

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Singleton handler for the workshop service protocol.
 *
 * The workshop service protocol currently has no requests of its own defined.
 * However, client control messages for the workshop in principle will go here.
 * @param myWorkshop The workshop server proper.
 */
internal class ClientHandler(private val myWorkshop: Workshop, commGorgel: Gorgel) : BasicProtocolHandler(commGorgel) {

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'workshop'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "workshop"
}
