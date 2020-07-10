package com.example.game.mods

import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

internal fun msgMove(who: Referenceable, x: Int, y: Int, into: Referenceable?): JsonLiteral =
        JsonLiteralFactory.targetVerb(who, "move").apply {
            addParameter("x", x)
            addParameter("y", y)
            addParameterOpt("into", into)
            finish()
        }
