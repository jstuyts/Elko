package org.elkoserver.objdb

import org.elkoserver.json.JSONLiteral
import java.util.function.Consumer

/**
 * A pending request to the repository.
 *
 * @param myHandler  Handler to call on the request result.
 * @param ref  Object reference being operated on.
 * @param myCollectionName  Name of collection to get from, or null to take
 *    the configured default.
 */
internal class PendingRequest(private val myHandler: Consumer<Any?>?, internal val ref: String, private val myCollectionName: String?, internal val tag: String, private val myMsg: JSONLiteral) {

    /**
     * Handle a reply from the repository.
     *
     * @param obj  The reply object.
     */
    fun handleReply(obj: Any?) {
        myHandler?.accept(obj)
    }

    /**
     * Transmit the request message to the repository.
     *
     * @param odbActor  Actor representing the connection to the repository.
     */
    fun sendRequest(odbActor: ODBActor) {
        odbActor.send(myMsg)
    }
}
