package com.example.game.mods

import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

internal fun msgSay(target: Referenceable, from: Referenceable, speech: String?): JsonLiteral =
        JsonLiteralFactory.targetVerb(target, "say").apply {
            addParameter("from", from)
            addParameter("speech", speech)
            finish()
        }
