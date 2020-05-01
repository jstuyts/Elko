package org.elkoserver.server.context

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.json.JSONLiteral

/**
 * A limbo [SendGroup], so actors can have a place to be when they aren't
 * anyplace.  Unlike a regular SendGroup, this one does not track its members:
 * anyone can declare themselves here or not, on their own say so.
 */
internal class LimboGroup private constructor() : SendGroup {
    /**
     * Add a new member to the group.  This is a no-op, since the limbo group
     * doesn't track its members.
     *
     * @param member  The new member to add.
     */
    override fun admitMember(member: Deliverer) {
        /* Nothing here. */
    }

    /**
     * Remove a member from the group.  This is a no-op, since the limbo group
     * doesn't track its members.
     *
     * @param member  The member to remove.
     */
    override fun expelMember(member: Deliverer) {
        /* Nothing here. */
    }

    /**
     * Send a message to every member of this group.  Messages sent to the
     * limbo group are simply discarded.
     *
     * @param message  The message to (not) send.
     */
    override fun send(message: JSONLiteral) {
        /* Nothing here. */
    }

    /**
     * Send a message to every member of this group except one.  Messages
     * sent to the limbo group are simply discarded.
     *
     * @param exclude  The member to exclude from receiving the message.
     * @param message  The message to send.
     */
    override fun sendToNeighbors(exclude: Deliverer, message: JSONLiteral) {
        /* Nothing here. */
    }

    companion object {
        /** Singleton LimboGroup instance.  Since all limbo groups are the same,
         * everyone might as well use this one.  */
        val theLimboGroup = LimboGroup()
    }
}
