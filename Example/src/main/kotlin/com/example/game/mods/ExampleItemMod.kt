package com.example.game.mods

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.ItemMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User

class ExampleItemMod @JsonMethod("str1", "str2", "int1", "int2") constructor(
        private val myString1: String,
        optString2: OptString,
        int1: Int,
        optInt2: OptInteger) : Mod(), ItemMod {
    private val myString2 = optString2.valueOrNull()
    private val myInt1 = int1
    private val myInt2 = optInt2.value(0)

    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("exi", control).apply {
                addParameter("str1", myString1)
                addParameterOpt("str2", myString2)
                addParameter("int1", myInt1)
                addParameter("int2", myInt2)
                finish()
            }

    @JsonMethod("arg", "otherarg")
    fun itemverb1(from: User, arg: String, otherArg: OptString) {
        ensureSameContext(from)
        context().send(msgItemVerb1(context(), from, arg, otherArg.valueOrNull()))
    }

    @JsonMethod("arg", "otherarg")
    fun itemverb2(from: User, arg: String, otherArg: OptString) {
        ensureSameContext(from)
        context().send(msgItemVerb2(context(), arg, otherArg.valueOrNull()))
    }
}
