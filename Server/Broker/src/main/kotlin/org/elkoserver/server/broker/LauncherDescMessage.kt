package org.elkoserver.server.broker

import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'launcherdesc' message.
 */
internal fun msgLauncherDesc(target: Referenceable, launchers: JsonLiteralArray) =
        JsonLiteralFactory.targetVerb(target, "launcherdesc").apply {
            addParameter("launchers", launchers)
            finish()
        }
