package org.elkoserver.server.context

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory

/**
 * Object representing the persistent information about the server state as a
 * whole that doesn't otherwise have an object to go in.
 */
internal class ServerDesc @JSONMethod("nextid") constructor(private var myNextID: Int) : Encodable {

    /**
     * Encode this server description for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("serverdesc", control).apply {
                addParameter("nextid", myNextID)
                finish()
            }

    /**
     * Get the next available item ID.
     *
     * @return the next item ID.
     */
    fun nextItemID() = "item-${myNextID++}"
}
