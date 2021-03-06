package org.elkoserver.server.presence.simple

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory

/**
 * A user's social graph, as represented in the object database.
 *
 * @param ref  The user's reference string
 * @param friends  Array of the user's friend references
 */
internal class UserGraphDesc @JsonMethod("ref", "friends") constructor(private val ref: String,
                                                                       internal val friends: Array<String>) : Encodable {

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            JsonLiteralFactory.type("ugraf", control).apply {
                addParameter("ref", ref)
                addParameter("friends", friends)
                finish()
            }
}
