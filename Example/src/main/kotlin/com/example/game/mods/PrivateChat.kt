package com.example.game.mods

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.json.EncodeControl
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User
import org.elkoserver.server.context.UserMod

/**
 * Ephemeral user mod to let users in a context talk privately to each other.
 */
class PrivateChat @JSONMethod constructor() : Mod(), UserMod {
    override fun encode(control: EncodeControl) = null

    @JSONMethod("speech")
    fun say(from: User, speech: String?) {
        ensureSameContext(from)
        val who = `object`() as User
        val response = SimpleChat.msgSay(who, from, speech)
        who.send(response)
        if (from !== who) {
            from.send(response)
        }
    }
}
