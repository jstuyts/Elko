package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralArray

/**
 * Generate a JSONLiteralArray of strings from a collection of strings.
 */
internal fun encodeStrings(strings: Collection<String>) =
        JsonLiteralArray().apply {
            for (str in strings) {
                addElement(str)
            }
            finish()
        }
