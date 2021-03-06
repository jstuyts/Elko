package org.elkoserver.server.context.test

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.model.ContextMod
import org.elkoserver.server.context.model.Mod
import org.elkoserver.server.context.model.User
import org.elkoserver.server.context.msgSay

/**
 * Mod to enable a context user to control the context's gate
 */
class GateController @JsonMethod constructor() : Mod(), ContextMod {
    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("gate", control).apply(JsonLiteral::finish)

    /**
     * Message handler for the 'gate' message.
     */
    @JsonMethod("open", "reason")
    fun gate(from: User, open: Boolean, optReason: OptString) {
        ensureSameContext(from)
        if (open) {
            context().openGate()
            from.send(msgSay(from, from, "gate opened"))
        } else {
            val reason = optReason.valueOrNull()
            context().closeGate(reason)
            from.send(msgSay(from, from, "gate closed: $reason"))
        }
    }
}
