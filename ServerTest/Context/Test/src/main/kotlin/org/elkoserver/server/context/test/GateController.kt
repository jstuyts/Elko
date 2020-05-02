package org.elkoserver.server.context.test

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.context.ContextMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.Msg.msgSay
import org.elkoserver.server.context.User

/**
 * Mod to enable a context user to control the context's gate
 */
class GateController @JSONMethod constructor() : Mod(), ContextMod {
    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("gate", control).apply {
                finish()
            }

    /**
     * Message handler for the 'gate' message.
     */
    @JSONMethod("open", "reason")
    fun gate(from: User, open: Boolean, optReason: OptString) {
        ensureSameContext(from)
        if (open) {
            context().openGate()
            from.send(msgSay(from, from, "gate opened"))
        } else {
            val reason = optReason.value<String?>(null)
            context().closeGate(reason)
            from.send(msgSay(from, from, "gate closed: $reason"))
        }
    }
}
