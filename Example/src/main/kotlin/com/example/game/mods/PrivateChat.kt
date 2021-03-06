package com.example.game.mods

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.server.context.model.Mod
import org.elkoserver.server.context.model.User
import org.elkoserver.server.context.model.UserMod

/**
 * Ephemeral user mod to let users in a context talk privately to each other.
 */
class PrivateChat @JsonMethod constructor() : Mod(), UserMod {
    override fun encode(control: EncodeControl): JsonLiteral? = null

    @JsonMethod("speech")
    fun say(from: User, speech: String) {
        ensureSameContext(from)
        val who = `object`() as User
        val response = msgSay(who, from, speech)
        who.send(response)
        if (from !== who) {
            from.send(response)
        }
    }
}
