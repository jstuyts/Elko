package org.elkoserver.foundation.json

import com.grack.nanojson.JsonObject

/**
 * Interface for an object to handle JSON messages addressed to it for which it
 * does not otherwise have specific methods.
 *
 * When the [MessageDispatcher] attempts to deliver a message to an
 * object, it first looks for a [JsonMethod] attributed method
 * corresponding to the specific message it is trying to deliver.  If such a
 * method is found, that method is invoked.  However, if there is no such
 * method, then the dispatcher checks whether the object implements this
 * interface; if it does, then it invokes the [ handleMessage()][.handleMessage] method to handle it.
 */
interface DefaultDispatchTarget {
    /**
     * Handle a message for which the [MessageDispatcher] could not
     * find an appropriate specific method to invoke.
     *
     * @param from  The sender of the message.
     * @param message  The message.
     *
     * @throws MessageHandlerException if there was some kind of problem
     * handling the message.
     */
    fun handleMessage(from: Deliverer, message: JsonObject)
}