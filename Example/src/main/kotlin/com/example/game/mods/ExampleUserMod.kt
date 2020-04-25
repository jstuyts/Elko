package com.example.game.mods

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.JSONLiteralFactory.targetVerb
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User
import org.elkoserver.server.context.UserMod

/**
 * An empty user mod, to get you started.
 */
class ExampleUserMod @JSONMethod constructor() : Mod(), UserMod {
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("exu", control).apply {
                finish()
            }

    @JSONMethod("arg", "otherarg")
    fun userverb(from: User, arg: String, otherArg: OptString) {
        ensureSameUser(from)
        from.send(msgUserVerb(from, arg, otherArg.value(null)))
    }

    companion object {
        private fun msgUserVerb(target: Referenceable, arg: String, otherArg: String) =
                targetVerb(target, "userverb").apply {
                    addParameter("arg", arg)
                    addParameterOpt("otherarg", otherArg)
                    finish()
                }
    }
}
