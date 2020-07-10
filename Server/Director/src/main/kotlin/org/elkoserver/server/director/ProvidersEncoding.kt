package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralArray

/**
 * Generate a JSONLiteralArray of provider labels from a set of provider
 * DirectorActor objects.
 */
internal fun encodeProviders(providers: Set<Provider>) =
        JsonLiteralArray().apply {
            for (subj in providers) {
                addElement(subj.actor.label)
            }
            finish()
        }
