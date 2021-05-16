package org.elkoserver.server.context.test

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.model.GeneralMod
import org.elkoserver.server.context.model.Mod
import org.elkoserver.server.context.model.User

/**
 * Mod to enable an object to function as a simple counter, mainly for testing
 * control over persistence.
 */
class Counter @JsonMethod("count") constructor(private var myCount: Int) : Mod(), GeneralMod {

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("counter", control).apply {
                addParameter("count", myCount)
                finish()
            }

    /**
     * Message handler for the 'inc' message.
     *
     * This message requests the counter to increment.
     *
     * <u>recv</u>: ` { to:*REF*, op:"inc" } `<br></br>
     *
     * <u>send</u>: ` { to:*REF*, op:"set",
     * from:*REF*, count:*int* } `
     *
     * @param from  The user requesting the census.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     * this mod, or if this mod is attached to a user and 'from' is not that
     * user.
     */
    @JsonMethod
    fun inc(from: User) {
        ensureSameContext(from)
        ++myCount
        markAsChanged()
        val announce = JsonLiteralFactory.targetVerb(`object`(), "set").apply {
            addParameter("count", myCount)
            finish()
        }
        context().send(announce)
    }
}
