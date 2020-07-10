package com.example.game.mods

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

internal fun msgItemVerb1(target: Referenceable, from: Referenceable, arg: String, otherArg: String?) =
        JsonLiteralFactory.targetVerb(target, "itemverb1").apply {
            addParameter("from", from)
            addParameter("arg", arg)
            addParameterOpt("otherarg", otherArg)
            finish()
        }
