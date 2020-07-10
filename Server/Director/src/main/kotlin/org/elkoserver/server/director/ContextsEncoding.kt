package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralArray

/**
 * Generate a JSONLiteralArray of context names from a sequence of
 * OpenContext objects.
 */
internal fun encodeContexts(contexts: Iterable<OpenContext>) =
        JsonLiteralArray().apply {
            for (context in contexts) {
                addElement(context.name)
            }
            finish()
        }
