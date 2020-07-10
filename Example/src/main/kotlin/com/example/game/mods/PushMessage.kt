package com.example.game.mods

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

internal fun msgPush(target: Referenceable, from: Referenceable,
                     url: String, frame: String?) =
        JsonLiteralFactory.targetVerb(target, "push").apply {
            addParameter("from", from)
            addParameter("url", url)
            addParameterOpt("frame", frame)
            finish()
        }
