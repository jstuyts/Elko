package com.example.game.mods

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.ContextMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User

/**
 * An empty context mod, to get you started.
 */
class ExampleContextMod @JsonMethod constructor() : Mod(), ContextMod {
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("exc", control).apply(JsonLiteral::finish)

    @JsonMethod("arg", "otherarg")
    fun ctxverb(from: User, arg: String, otherArg: OptString) {
        ensureSameContext(from)
        context().send(msgCtxVerb(context(), from, arg, otherArg.valueOrNull()))
    }
}
