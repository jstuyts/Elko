package org.elkoserver.server.presence

import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'dump' message.
 */
internal fun msgDump(target: Referenceable, numUsers: Int, numPresences: Int, userDump: JsonLiteralArray?) =
        JsonLiteralFactory.targetVerb(target, "dump").apply {
            addParameter("numusers", numUsers)
            addParameter("numpresences", numPresences)
            if (userDump != null) {
                addParameter("users", userDump)
            }
            finish()
        }
