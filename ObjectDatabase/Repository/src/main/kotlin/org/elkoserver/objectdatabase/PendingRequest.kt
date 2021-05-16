package org.elkoserver.objectdatabase

import org.elkoserver.json.JsonLiteral
import java.util.function.Consumer

/**
 * A pending request to the repository.
 *
 * @param myHandler  Handler to call on the request result.
 * @param ref  Object reference being operated on.
 */
internal class PendingRequest(private val myHandler: Consumer<Any?>?, internal val ref: String, internal val tag: String, private val myMsg: JsonLiteral) {

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
     * @param objectDatabaseRepositoryActor  Actor representing the connection to the repository.
     */
    fun sendRequest(objectDatabaseRepositoryActor: ObjectDatabaseRepositoryActor) {
        objectDatabaseRepositoryActor.send(myMsg)
    }
}
