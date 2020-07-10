package org.elkoserver.feature.basicexamples.census

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.GeneralMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User

/**
 * Mod to enable tracking a context's population.  This mod may be attached to
 * a context, user or item.
 */
class Census @JsonMethod constructor() : Mod(), GeneralMod {
    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl) =
            if (control.toRepository()) {
                JsonLiteralFactory.type("census", control).apply {
                    finish()
                }
            } else {
                null
            }

    /**
     * Message handler for the 'census' message.
     *
     * This message requests the current number of users in the context where
     * this mod resides.
     *
     * <u>recv</u>: ` { to:*REF*, op:"census" } `<br></br>
     *
     * <u>send</u>: ` { to:*REF*, op:"census",
     * occupancy:*int* } `
     *
     * @param from  The user requesting the census.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     * this mod, or if this mod is attached to a user and 'from' is not that
     * user.
     */
    @JsonMethod
    fun census(from: User) {
        ensureSameContext(from)
        if (`object`() is User) {
            ensureSameUser(from)
        }
        val response = JsonLiteralFactory.targetVerb(`object`(), "census").apply {
            addParameter("occupancy", context().userCount)
            finish()
        }
        from.send(response)
    }
}
