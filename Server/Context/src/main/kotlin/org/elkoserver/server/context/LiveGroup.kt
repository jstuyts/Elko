package org.elkoserver.server.context

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.json.JSONLiteral
import java.util.Collections
import java.util.HashSet

/**
 * The normal, ordinary implementation of [SendGroup].
 */
open class LiveGroup : SendGroup {
    /** The objects in this send group.  */
    private val myMembers: MutableSet<Deliverer> = HashSet()

    /**
     * Add a new member to the send group.
     *
     * @param member  The new member to add.
     */
    override fun admitMember(member: Deliverer) {
        myMembers.add(member)
    }

    /**
     * Remove a member from the send group.
     *
     * @param member  The member to remove.
     */
    override fun expelMember(member: Deliverer) {
        myMembers.remove(member)
    }

    /**
     * Get a read-only view of the set of members of this send group.
     *
     * @return the current set of members of this group.
     */
    fun members(): Set<Deliverer> {
        return Collections.unmodifiableSet(myMembers)
    }

    /**
     * Send a message to each member of this send group.
     *
     * @param message  The message to send.
     */
    override fun send(message: JSONLiteral) {
        for (member in myMembers) {
            member.send(message)
        }
    }

    /**
     * Send a message to every member of this send group except one.
     *
     * @param exclude  The member to exclude from receiving the message.
     * @param message  The message to send.
     */
    override fun sendToNeighbors(exclude: Deliverer, message: JSONLiteral) {
        for (member in myMembers) {
            if (member !== exclude) {
                member.send(message)
            }
        }
    }
}