package com.example.game.mods

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

internal fun msgItemVerb2(target: Referenceable, arg: String, otherArg: String?) =
        JsonLiteralFactory.targetVerb(target, "itemverb2").apply {
            addParameter("arg", arg)
            addParameterOpt("otherarg", otherArg)
            finish()
        }
