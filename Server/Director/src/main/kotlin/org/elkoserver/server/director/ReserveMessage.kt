package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

internal fun msgReserve(target: Referenceable, context: String?, user: String?, hostPort: String?, reservation: String?, deny: String?, tag: String?) =
        JsonLiteralFactory.targetVerb(target, "reserve").apply {
            addParameter("context", context)
            addParameterOpt("user", user)
            addParameterOpt("hostport", hostPort)
            addParameterOpt("reservation", reservation)
            addParameterOpt("deny", deny)
            addParameterOpt("tag", tag)
            finish()
        }
