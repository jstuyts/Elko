package com.example.game.mods

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

internal fun msgUserVerb(target: Referenceable, arg: String, otherArg: String?) =
        JsonLiteralFactory.targetVerb(target, "userverb").apply {
            addParameter("arg", arg)
            addParameterOpt("otherarg", otherArg)
            finish()
        }
