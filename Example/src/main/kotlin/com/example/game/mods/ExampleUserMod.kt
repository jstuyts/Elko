package com.example.game.mods

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.model.Mod
import org.elkoserver.server.context.model.User
import org.elkoserver.server.context.model.UserMod

/**
 * An empty user mod, to get you started.
 */
class ExampleUserMod @JsonMethod constructor() : Mod(), UserMod {
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("exu", control).apply(JsonLiteral::finish)

    @JsonMethod("arg", "otherarg")
    fun userverb(from: User, arg: String, otherArg: OptString) {
        ensureSameUser(from)
        from.send(msgUserVerb(from, arg, otherArg.valueOrNull()))
    }
}
