package org.elkoserver.foundation.server

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'load' message: report this server's load to the broker.
 *
 * @param target  Object the message is being sent to.
 * @param factor  Load factor to report.
 */
internal fun msgLoad(target: Referenceable, factor: Double) =
        JsonLiteralFactory.targetVerb(target, "load").apply {
            addParameter("factor", factor)
            finish()
        }
