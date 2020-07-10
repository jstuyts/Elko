package org.elkoserver.server.context

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.json.JsonLiteral

/**
 * Deliverer object that delivers to all the members of a send group except
 * one.
 *
 * @param myGroup  The send group.
 * @param myExclusion  Who to leave out.
 */
internal class Neighbors(private val myGroup: SendGroup, private val myExclusion: Deliverer) : Deliverer {

    /**
     * Send a message to every member of the send group except the excluded
     * one.
     *
     * @param message  The message to send.
     */
    override fun send(message: JsonLiteral) {
        myGroup.sendToNeighbors(myExclusion, message)
    }
}
