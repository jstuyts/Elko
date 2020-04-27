package com.example.game.mods

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.JSONLiteralFactory.targetVerb
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.ItemMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User

class ExampleItemMod @JSONMethod("str1", "str2", "int1", "int2") constructor(
        private val myString1: String,
        optString2: OptString,
        int1: Int,
        optInt2: OptInteger) : Mod(), ItemMod {
    private val myString2: String = optString2.value(null)
    private val myInt1: Int = int1
    private val myInt2: Int = optInt2.value(0)

    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("exi", control).apply {
                addParameter("str1", myString1)
                addParameterOpt("str2", myString2)
                addParameter("int1", myInt1)
                addParameter("int2", myInt2)
                finish()
            }

    @JSONMethod("arg", "otherarg")
    fun itemverb1(from: User, arg: String, otherArg: OptString) {
        ensureSameContext(from)
        context().send(msgItemVerb1(context(), from, arg, otherArg.value(null)))
    }

    @JSONMethod("arg", "otherarg")
    fun itemverb2(from: User?, arg: String, otherArg: OptString) {
        ensureSameContext(from)
        context().send(msgItemVerb2(context(), arg, otherArg.value(null)))
    }

    companion object {
        private fun msgItemVerb1(target: Referenceable, from: Referenceable, arg: String, otherArg: String) =
                targetVerb(target, "itemverb1").apply {
                    addParameter("from", from)
                    addParameter("arg", arg)
                    addParameterOpt("otherarg", otherArg)
                    finish()
                }

        private fun msgItemVerb2(target: Referenceable, arg: String, otherArg: String) =
                targetVerb(target, "itemverb2").apply {
                    addParameter("arg", arg)
                    addParameterOpt("otherarg", otherArg)
                    finish()
                }
    }
}