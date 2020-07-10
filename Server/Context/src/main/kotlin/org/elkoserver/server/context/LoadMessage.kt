package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteralFactory

/**
 * Create a "load" message.
 *
 * @param factor  Load factor to report.
 */
internal fun msgLoad(factor: Double) =
        JsonLiteralFactory.targetVerb("provider", "load").apply {
            addParameter("factor", factor)
            finish()
        }
