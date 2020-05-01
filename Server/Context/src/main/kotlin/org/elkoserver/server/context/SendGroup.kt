package org.elkoserver.server.context

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.json.JSONLiteral

/**
 * A collection of Deliverers treated as a unit.  Invoking the send() method on
 * a SendGroup is equivalent to invoking the send() method individually (with
 * the same message parameter) on each member of the group, though no promises
 * are made about the order in which the various members will be sent to.
 */
interface SendGroup : Deliverer {
    /**
     * Add a new member to the group.
     *
     * @param member  The new member to add.
     */
    fun admitMember(member: Deliverer)

    /**
     * Remove a member from the group.  It is not an error for the indicated
     * object to not be a member; this method simply ensures that after it is
     * called, the given object is not a member.
     *
     * @param member  The member to remove.
     */
    fun expelMember(member: Deliverer)

    /**
     * Send a message to every member of this group except one.
     *
     * @param exclude  The member to exclude from receiving the message.
     * @param message  The message to send.
     */
    fun sendToNeighbors(exclude: Deliverer, message: JSONLiteral)
}