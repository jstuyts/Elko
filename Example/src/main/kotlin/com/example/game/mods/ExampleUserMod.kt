package com.example.game.mods

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User
import org.elkoserver.server.context.UserMod

/**
 * An empty user mod, to get you started.
 */
class ExampleUserMod @JsonMethod constructor() : Mod(), UserMod {
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("exu", control).apply {
                finish()
            }

    @JsonMethod("arg", "otherarg")
    fun userverb(from: User, arg: String, otherArg: OptString) {
        ensureSameUser(from)
        from.send(msgUserVerb(from, arg, otherArg.value<String?>(null)))
    }
}
