package com.example.game.mods

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.JSONLiteralFactory.targetVerb
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.ContextMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User

/**
 * An empty context mod, to get you started.
 */
class ExampleContextMod @JSONMethod constructor() : Mod(), ContextMod {
    override fun encode(control: EncodeControl): JSONLiteral =
            JSONLiteralFactory.type("exc", control).apply {
                finish()
            }

    @JSONMethod("arg", "otherarg")
    fun ctxverb(from: User, arg: String, otherArg: OptString) {
        ensureSameContext(from)
        context().send(msgCtxVerb(context(), from, arg, otherArg.value(null)))
    }

    companion object {
        private fun msgCtxVerb(target: Referenceable, from: Referenceable, arg: String, otherArg: String) =
                targetVerb(target, "ctxverb").apply {
                    addParameter("from", from)
                    addParameter("arg", arg)
                    addParameterOpt("otherarg", otherArg)
                    finish()
                }
    }
}
