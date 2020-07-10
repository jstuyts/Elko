package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

internal fun msgDoReserve(target: Referenceable, context: String?, user: String?, reservation: String) =
        JsonLiteralFactory.targetVerb(target, "doreserve").apply {
            addParameter("context", context)
            addParameterOpt("user", user)
            addParameterOpt("reservation", reservation)
            finish()
        }
